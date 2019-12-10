package com.example.musicbrainzkotlin

import android.os.Bundle
import android.util.Log
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var search: SearchView
    private lateinit var timer: Timer
    private lateinit var points: ArrayList<Point>
    private var seconds = 0
    private var requests = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        search = findViewById(R.id.search)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        timer = Timer()
        points = ArrayList()
        searchSetup()
    }

    private fun searchSetup() {
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override fun onQueryTextChange(newText: String): Boolean {
                if (newText.isBlank()) {
                    mMap.clear()
                    timer.cancel()
                    seconds = 0
                    points = ArrayList()

                }
                return false
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                runCoroutineHTTP(query, 20, 0)
                return false
            }

        })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
    }

    private fun runCoroutineHTTP(query: String, limit: Int, offset: Int) {
        GlobalScope.launch {
            val resultJSON = httpGet(query, limit, offset)
            val places: JSONArray = resultJSON.getJSONArray("places")
            var coordinateList = HashMap<Int, LatLng>()

            for (i in 0 until places.length()) {
                val place: JSONObject = places.getJSONObject(i)
                if (place.has("coordinates") && place.has("life-span")) {
                    if (place.getJSONObject("life-span").has("begin")) {
                        val beginDate =
                            place.getJSONObject("life-span").getString("begin").substring(0, 4)
                                .toInt()
                        Log.e("---", beginDate.toString())
                        if (beginDate >= 1990) {
                            val lat =
                                place.getJSONObject("coordinates").getString("latitude").toDouble()
                            val lng =
                                place.getJSONObject("coordinates").getString("longitude").toDouble()
                            val lifeSpan = beginDate - 1990
                            coordinateList[lifeSpan] = LatLng(lat, lng)
                        }
                    }
                }
            }
            addMarkers(coordinateList)

            if ((offset + limit) < resultJSON.getInt("count")) {
                runCoroutineHTTP(query, limit, offset + limit)
            } else {
                showRequestsNumber()
                activateTimer()
            }
        }
    }

    private fun showRequestsNumber() {
        this@MapsActivity.runOnUiThread(Runnable {
            val toast =
                Toast.makeText(applicationContext, "Requests: $requests", Toast.LENGTH_SHORT)
            toast.show()
            requests = 0
        })
    }

    private fun activateTimer() {
        timer.schedule(object : TimerTask() {
            override fun run() {
                this@MapsActivity.runOnUiThread(Runnable {
                    refreshMarkers(seconds)
                    seconds += 1
                })
            }
        }, 1, 1000)
    }

    private fun refreshMarkers(secondssElapsed: Int) {
        for (point in points) {
            if ((point.lifespan - secondssElapsed) == 0) {
                point.marker.remove()
            }
        }
    }

    private fun addMarkers(coordinateMap: HashMap<Int, LatLng>) {
        this@MapsActivity.runOnUiThread(Runnable {
            for ((lifespan, position) in coordinateMap) {
                val marker = mMap.addMarker(MarkerOptions().position(position))
                points.add(Point(marker, lifespan))
            }
        })
    }

    private suspend fun httpGet(query: String, limit: Int, offset: Int): JSONObject {
        requests += 1
        val response = withContext(Dispatchers.IO) {
            val result = try {
                URL("http://musicbrainz.org/ws/2/place/?query=$query&limit=$limit&offset=$offset&fmt=json")
                    .openStream()
                    .bufferedReader()
                    .use { it.readText() }
            } catch (e: IOException) {
                "Error with ${e.message}."
            }
            JSONObject(result)
        }
        return response
    }
}
