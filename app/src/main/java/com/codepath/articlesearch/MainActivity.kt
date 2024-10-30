package com.codepath.articlesearch

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.codepath.articlesearch.databinding.ActivityMainBinding
import com.codepath.asynchttpclient.AsyncHttpClient
import com.codepath.asynchttpclient.callback.JsonHttpResponseHandler
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Headers
import org.json.JSONException

fun createJson() = Json {
    isLenient = true
    ignoreUnknownKeys = true
    useAlternativeNames = false
}

private const val TAG = "MainActivity/"
private const val SEARCH_API_KEY = BuildConfig.API_KEY
private const val ARTICLE_SEARCH_URL =
    "https://api.nytimes.com/svc/search/v2/articlesearch.json?api-key=${SEARCH_API_KEY}"

class MainActivity : AppCompatActivity(), NetworkReceiver.NetworkCallback {
    private val articles = mutableListOf<DisplayArticle>()
    private lateinit var articlesRecyclerView: RecyclerView
    private lateinit var binding: ActivityMainBinding
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var networkReceiver: NetworkReceiver
    private lateinit var buttonSettings: Button

    //private lateinit val buttonSettings = findViewById<Button>(R.id.button_settings)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        articlesRecyclerView = findViewById(R.id.articles)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        buttonSettings = binding.buttonSettings
        val articleAdapter = ArticleAdapter(this, articles)
        articlesRecyclerView.adapter = articleAdapter
        articlesRecyclerView.layoutManager = LinearLayoutManager(this).also {
            val dividerItemDecoration = DividerItemDecoration(this, it.orientation)
            articlesRecyclerView.addItemDecoration(dividerItemDecoration)
        }

        buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        // Swipe-to-refresh listener that reloads data
        swipeRefreshLayout.setOnRefreshListener {
            loadData(articleAdapter) // Call the existing data load function
        }

        // Load data on app start
        loadData(articleAdapter)

        // Initialize network receiver
        networkReceiver = NetworkReceiver(this)
        val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkReceiver, intentFilter)
    }

    private fun loadData(articleAdapter: ArticleAdapter) {
        lifecycleScope.launch {
            (application as ArticleApplication).db.articleDao().getAll().collect { databaseList ->
                databaseList.map { entity ->
                    DisplayArticle(
                        entity.headline,
                        entity.articleAbstract,
                        entity.byline,
                        entity.mediaImageUrl
                    )
                }.also { mappedList ->
                    articles.clear()
                    articles.addAll(mappedList)
                    articleAdapter.notifyDataSetChanged()
                }
            }
        }

        val client = AsyncHttpClient()
        client.get(ARTICLE_SEARCH_URL, object : JsonHttpResponseHandler() {
            override fun onFailure(
                statusCode: Int,
                headers: Headers?,
                response: String?,
                throwable: Throwable?
            ) {
                Log.e(TAG, "Failed to fetch articles: $statusCode")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Unable to refresh data. Please check your connection.", Toast.LENGTH_SHORT).show()
                }
                swipeRefreshLayout.isRefreshing = false
            }

            override fun onSuccess(statusCode: Int, headers: Headers, json: JSON) {
                Log.i(TAG, "Successfully fetched articles: $json")
                try {
                    val parsedJson = createJson().decodeFromString(
                        SearchNewsResponse.serializer(),
                        json.jsonObject.toString()
                    )
                    parsedJson.response?.docs?.let { list ->

                        // Check the user’s caching preference
                        val sharedPreferences = getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
                        val shouldCacheData = sharedPreferences.getBoolean("cache_data", true)

                        if (shouldCacheData) {
                            // Cache data only if the user has enabled caching
                            lifecycleScope.launch(IO) {
                                (application as ArticleApplication).db.articleDao().deleteAll()
                                (application as ArticleApplication).db.articleDao().insertAll(list.map {
                                    ArticleEntity(
                                        headline = it.headline?.main,
                                        articleAbstract = it.abstract,
                                        byline = it.byline?.original,
                                        mediaImageUrl = it.mediaImageUrl
                                    )
                                })
                            }
                        }

                        // Update UI on the main thread
                        lifecycleScope.launch {
                            articles.clear()
                            articles.addAll(list.map {
                                DisplayArticle(
                                    it.headline?.main,
                                    it.abstract,
                                    it.byline?.original,
                                    it.mediaImageUrl
                                )
                            })
                            articleAdapter.notifyDataSetChanged()
                            swipeRefreshLayout.isRefreshing = false
                        }
                    }
                } catch (e: JSONException) {
                    Log.e(TAG, "Exception: $e")
                } finally {
                    // Stop the refreshing animation
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        })
    }

    override fun onNetworkAvailable() {
        // Hide offline UI if it's displayed and reload data
        loadData((articlesRecyclerView.adapter as ArticleAdapter))
    }

    override fun onNetworkUnavailable() {
        showOfflineUI()
    }

    private fun showOfflineUI() {
        Snackbar.make(findViewById(android.R.id.content), "You are offline. Please check your internet connection.", Snackbar.LENGTH_INDEFINITE)
            .setAction("Retry") {
                loadData((articlesRecyclerView.adapter as ArticleAdapter))  // Optionally retry loading data
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(networkReceiver)  // Don't forget to unregister the receiver
    }
}
