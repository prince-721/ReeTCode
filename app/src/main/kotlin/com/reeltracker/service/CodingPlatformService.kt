package com.reeltracker.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "CodingPlatformService"

data class SolvedProblem(
    val title: String,
    val titleSlug: String,
    val timestamp: Long
)

data class PlatformProfile(
    val username: String,
    val totalSolved: Int,
    val rating: String,
    val avatarUrl: String,
    val bio: String = ""
)

sealed class CodingResult<out T> {
    data class Success<T>(val data: T) : CodingResult<T>()
    data class Error(val message: String) : CodingResult<Nothing>()
}

object CodingPlatformService {

    private const val LEETCODE_GRAPHQL = "https://leetcode.com/graphql"
    private const val CODECHEF_API = "https://codechef-api.vercel.app/handle"

    // ---- LeetCode ----

    suspend fun verifyLeetCodeUsername(username: String): CodingResult<PlatformProfile> =
        withContext(Dispatchers.IO) {
            try {
                val query = """
                    {
                        matchedUser(username: "$username") {
                            username
                            profile {
                                userAvatar
                                ranking
                                aboutMe
                            }
                            submitStatsGlobal {
                                acSubmissionNum {
                                    difficulty
                                    count
                                }
                            }
                        }
                    }
                """.trimIndent()

                val body = JSONObject().apply {
                    put("query", query)
                }.toString()

                val response = postJson(LEETCODE_GRAPHQL, body)
                if (response == null) {
                    return@withContext CodingResult.Error("Could not reach LeetCode, try again")
                }

                val data = response.optJSONObject("data")
                val matchedUser = data?.optJSONObject("matchedUser")
                if (matchedUser == null) {
                    return@withContext CodingResult.Error("LeetCode username not found")
                }

                val profile = matchedUser.optJSONObject("profile")
                val submitStats = matchedUser.optJSONObject("submitStatsGlobal")
                val acArray = submitStats?.optJSONArray("acSubmissionNum")

                var totalSolved = 0
                if (acArray != null) {
                    for (i in 0 until acArray.length()) {
                        val item = acArray.getJSONObject(i)
                        if (item.getString("difficulty") == "All") {
                            totalSolved = item.getInt("count")
                            break
                        }
                    }
                }

                val ranking = profile?.optInt("ranking", 0) ?: 0
                val avatarUrl = profile?.optString("userAvatar", "") ?: ""
                val bio = profile?.optString("aboutMe", "") ?: ""

                CodingResult.Success(
                    PlatformProfile(
                        username = matchedUser.getString("username"),
                        totalSolved = totalSolved,
                        rating = if (ranking > 0) "#$ranking" else "Unranked",
                        avatarUrl = avatarUrl,
                        bio = bio
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "LeetCode verify failed", e)
                CodingResult.Error("Could not reach LeetCode, try again")
            }
        }

    suspend fun fetchLeetCodeRecentSubmissions(
        username: String,
        sinceMs: Long
    ): CodingResult<List<SolvedProblem>> = withContext(Dispatchers.IO) {
        try {
            val query = """
                {
                    recentAcSubmissionList(username: "$username", limit: 20) {
                        title
                        titleSlug
                        timestamp
                    }
                }
            """.trimIndent()

            val body = JSONObject().apply {
                put("query", query)
            }.toString()

            val response = postJson(LEETCODE_GRAPHQL, body)
            if (response == null) {
                return@withContext CodingResult.Error("Could not reach LeetCode, try again")
            }

            val data = response.optJSONObject("data")
            val submissions = data?.optJSONArray("recentAcSubmissionList")
            if (submissions == null) {
                return@withContext CodingResult.Success(emptyList())
            }

            val sinceSeconds = sinceMs / 1000
            val problems = mutableMapOf<String, SolvedProblem>()

            for (i in 0 until submissions.length()) {
                val sub = submissions.getJSONObject(i)
                val timestamp = sub.getString("timestamp").toLongOrNull() ?: continue
                if (timestamp >= sinceSeconds) {
                    val titleSlug = sub.getString("titleSlug")
                    // Keep the most recent submission for each unique problem
                    if (!problems.containsKey(titleSlug)) {
                        problems[titleSlug] = SolvedProblem(
                            title = sub.getString("title"),
                            titleSlug = titleSlug,
                            timestamp = timestamp * 1000 // convert back to millis
                        )
                    }
                }
            }

            CodingResult.Success(problems.values.toList().sortedByDescending { it.timestamp })
        } catch (e: Exception) {
            Log.e(TAG, "LeetCode fetch failed", e)
            CodingResult.Error("Could not reach LeetCode, try again")
        }
    }

    // ---- CodeChef ----

    suspend fun verifyCodeChefUsername(username: String): CodingResult<PlatformProfile> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(username, "UTF-8")
                val url = URL("https://www.codechef.com/users/$encoded")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    val code = connection.responseCode
                    connection.disconnect()
                    if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                        return@withContext CodingResult.Error("CodeChef username not found")
                    } else {
                        return@withContext CodingResult.Error("Could not reach CodeChef (HTTP $code), try again")
                    }
                }

                val html = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                connection.disconnect()

                // Parse Full Name
                val nameRegex = Regex("""<h1[^>]*class=["']h2-style["'][^>]*>([^<]+)</h1>""", RegexOption.IGNORE_CASE)
                val name = nameRegex.find(html)?.groupValues?.get(1)?.trim() ?: username

                // Parse Rating
                val ratingRegex = Regex("""<div[^>]*class=["']rating-number["'][^>]*>\s*(\d+)""", RegexOption.IGNORE_CASE)
                val rating = ratingRegex.find(html)?.groupValues?.get(1)?.trim() ?: "0"

                // Parse Stars
                val starBlockRegex = Regex("""<div[^>]*class=["']rating-star["'][^>]*>(.*?)</div>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                val starBlock = starBlockRegex.find(html)?.groupValues?.get(1) ?: ""
                val starCount = Regex("&#9733;").findAll(starBlock).count()
                val stars = if (starCount > 0) "$starCount★" else ""

                // Parse Total Solved Count
                val solvedRegex = Regex("""<h3>Total Problems Solved:\s*(\d+)</h3>""", RegexOption.IGNORE_CASE)
                val totalSolved = solvedRegex.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0

                CodingResult.Success(
                    PlatformProfile(
                        username = username,
                        totalSolved = totalSolved,
                        rating = if (rating != "0") "$rating $stars" else stars.ifEmpty { "Unrated" },
                        avatarUrl = "https://cdn.codechef.com/sites/all/themes/abessive/images/user_default_thumb.jpg",
                        bio = name // Store Full Name in bio so we can check it for verification code
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "CodeChef direct verify failed", e)
                CodingResult.Error("Could not reach CodeChef, try again")
            }
        }

    suspend fun fetchCodeChefSolvedCount(username: String): CodingResult<Int> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(username, "UTF-8")
                val url = URL("https://www.codechef.com/users/$encoded")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    connection.disconnect()
                    return@withContext CodingResult.Error("Could not reach CodeChef")
                }

                val html = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                connection.disconnect()

                val solvedRegex = Regex("""<h3>Total Problems Solved:\s*(\d+)</h3>""", RegexOption.IGNORE_CASE)
                val totalSolved = solvedRegex.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0

                CodingResult.Success(totalSolved)
            } catch (e: Exception) {
                Log.e(TAG, "CodeChef direct solved count fetch failed", e)
                CodingResult.Error("Could not reach CodeChef")
            }
        }

    // ---- GeeksforGeeks ----

    suspend fun verifyGfgUsername(username: String): CodingResult<PlatformProfile> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(username, "UTF-8")
                val url = URL("https://www.geeksforgeeks.org/profile/$encoded")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    val code = connection.responseCode
                    connection.disconnect()
                    if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                        return@withContext CodingResult.Error("GeeksforGeeks username not found")
                    } else {
                        return@withContext CodingResult.Error("Could not reach GeeksforGeeks (HTTP $code), try again")
                    }
                }

                val html = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                connection.disconnect()

                val solvedRegex = Regex("""total_problems_solved.*?:\s*(\d+)""", RegexOption.IGNORE_CASE)
                val totalSolved = solvedRegex.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0

                CodingResult.Success(
                    PlatformProfile(
                        username = username,
                        totalSolved = totalSolved,
                        rating = "Solved: $totalSolved",
                        avatarUrl = "https://media.geeksforgeeks.org/gfg-gg-logo.svg",
                        bio = html // HTML content of GFG profile to check for bio code
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "GFG direct verify failed", e)
                CodingResult.Error("Could not reach GeeksforGeeks, try again")
            }
        }

    suspend fun fetchGfgSolvedCount(username: String): CodingResult<Int> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(username, "UTF-8")
                val url = URL("https://www.geeksforgeeks.org/profile/$encoded")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    connection.disconnect()
                    return@withContext CodingResult.Error("Could not reach GeeksforGeeks")
                }

                val html = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                connection.disconnect()

                val solvedRegex = Regex("""total_problems_solved.*?:\s*(\d+)""", RegexOption.IGNORE_CASE)
                val totalSolved = solvedRegex.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0

                CodingResult.Success(totalSolved)
            } catch (e: Exception) {
                Log.e(TAG, "GFG direct solved count fetch failed", e)
                CodingResult.Error("Could not reach GeeksforGeeks")
            }
        }

    suspend fun fetchCodeChefRecentSubmissions(
        username: String,
        sinceMs: Long
    ): CodingResult<List<SolvedProblem>> = withContext(Dispatchers.IO) {
        try {
            val response = getJson("$CODECHEF_API/$username")
            if (response == null) {
                return@withContext CodingResult.Error("Could not reach CodeChef, try again")
            }

            val success = response.optBoolean("success", false)
            if (!success) {
                return@withContext CodingResult.Error("CodeChef username not found")
            }

            val problems = mutableListOf<SolvedProblem>()

            // Parse the heatMap which contains date -> submission count entries
            val heatMap = response.optJSONArray("heatMap")
            if (heatMap != null) {
                for (i in 0 until heatMap.length()) {
                    val entry = heatMap.getJSONObject(i)
                    val date = entry.optString("date", "")
                    val value = entry.optInt("value", 0)
                    if (date.isNotEmpty() && value > 0) {
                        // Convert date string to timestamp
                        try {
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            val dateMs = sdf.parse(date)?.time ?: continue
                            if (dateMs >= sinceMs) {
                                problems.add(
                                    SolvedProblem(
                                        title = "CodeChef Problem ($date)",
                                        titleSlug = "codechef_${date}_$i",
                                        timestamp = dateMs
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            // Skip unparseable dates
                        }
                    }
                }
            }

            // Also try recentActivity if available
            val recentActivity = response.optJSONArray("recentActivity")
            if (recentActivity != null) {
                val seen = mutableSetOf<String>()
                for (i in 0 until recentActivity.length()) {
                    val entry = recentActivity.getJSONObject(i)
                    val name = entry.optString("name", entry.optString("problemCode", ""))
                    val time = entry.optLong("time", 0L)
                    val timeMs = if (time < 10_000_000_000L) time * 1000 else time
                    if (name.isNotEmpty() && timeMs >= sinceMs && seen.add(name)) {
                        problems.add(
                            SolvedProblem(
                                title = name,
                                titleSlug = name,
                                timestamp = timeMs
                            )
                        )
                    }
                }
            }

            CodingResult.Success(
                problems.distinctBy { it.titleSlug }.sortedByDescending { it.timestamp }
            )
        } catch (e: Exception) {
            Log.e(TAG, "CodeChef fetch failed", e)
            CodingResult.Error("Could not reach CodeChef, try again")
        }
    }

    // ---- HTTP Helpers ----

    private fun postJson(urlString: String, body: String): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body)
                writer.flush()
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val text = BufferedReader(InputStreamReader(connection.inputStream)).use {
                    it.readText()
                }
                JSONObject(text)
            } else {
                Log.e(TAG, "POST $urlString returned ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "POST $urlString failed", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun getJson(urlString: String): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                connectTimeout = 15_000
                readTimeout = 15_000
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val text = BufferedReader(InputStreamReader(connection.inputStream)).use {
                    it.readText()
                }
                JSONObject(text)
            } else {
                Log.e(TAG, "GET $urlString returned ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET $urlString failed", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
