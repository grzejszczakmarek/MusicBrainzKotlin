package com.example.musicbrainzkotlin

import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.EditText
import android.widget.SearchView
import android.widget.SearchView.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
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
    private var limit = 20
    private var timerStarted = false
    private var timerLifespan = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        search = findViewById(R.id.search)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        timer = Timer()
        points = ArrayList()
        searchSetup()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val menuInflater = menuInflater
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        openModal()
        return super.onOptionsItemSelected(item)
    }

    private fun openModal() {
        val editText = EditText(this)
        editText.setRawInputType(InputType.TYPE_CLASS_NUMBER)
        editText.setText(limit.toString())

        val alert = AlertDialog.Builder(this)
            .setTitle("Set limit for requests")
            .setView(editText)

        alert.setPositiveButton("Ok") { _, _ ->

            if (!editText.text.toString().isBlank() && editText.text.toString().toInt() >= 1 && editText.text.toString().toInt() <= 100) {
                limit = editText.text.toString().toInt()
            } else {
                Toast.makeText(applicationContext, "Limit should be number between 1 and 100",Toast.LENGTH_LONG).show()
            }
        }
        alert.show()
    }

    private fun searchSetup() {
        search.setOnQueryTextListener(object : OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                if (newText.isBlank()) {
                    reset()
                }
                return false
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                runCoroutineHTTP(query, limit, 0)
                return false
            }
        })
    }

    private fun reset() {
        mMap.clear()
        if(timerStarted) {
            timer.cancel()
            timerStarted = false
        }
        seconds = 0
        points = ArrayList()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
    }

    private fun runCoroutineHTTP(query: String, limit: Int, offset: Int) {
        GlobalScope.launch {
            val resultJSON = httpGet(query, limit, offset)

            if (!resultJSON.has("places")) {
                showError()
                return@launch
            }

            val places: JSONArray = resultJSON.getJSONArray("places")
            val coordinateList = HashMap<Int, LatLng>()

            for (i in 0 until places.length()) {
                val place: JSONObject = places.getJSONObject(i)
                if (place.has("coordinates") && place.has("life-span")) {
                    if (place.getJSONObject("life-span").has("begin")) {
                        val beginDate =
                            place.getJSONObject("life-span").getString("begin").substring(0, 4)
                                .toInt()
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

    private fun showError() {
        this@MapsActivity.runOnUiThread {
            Toast.makeText(applicationContext, "Error in JSON",Toast.LENGTH_LONG).show()
            reset()
            requests = 0
        }
    }

    private fun showRequestsNumber() {
        this@MapsActivity.runOnUiThread {
            Toast.makeText(applicationContext, "Requests: $requests",Toast.LENGTH_LONG).show()
        }
    }

    private fun activateTimer() {
        timerLifespan = points.size
        timer.schedule(object : TimerTask() {
            override fun run() {
                this@MapsActivity.runOnUiThread {
                    refreshMarkers(seconds)
                    seconds += 1
                    timerLifespan -=1
                    timerStarted = true
                }
            }
        }, 1, 1000)
    }


    private fun addMarkers(coordinateMap: HashMap<Int, LatLng>) {
        this@MapsActivity.runOnUiThread {
            for ((lifespan, position) in coordinateMap) {
                val marker = mMap.addMarker(MarkerOptions().position(position))
                points.add(Point(marker, lifespan))
            }
        }
    }

    private fun refreshMarkers(secondssElapsed: Int) {
        for (point in points) {
            if ((point.lifespan - secondssElapsed) == 0) {
                point.marker.remove()
            }
        }
        if(timerLifespan == 0){
            reset()
        }
    }

    private suspend fun httpGet(query: String, limit: Int, offset: Int): JSONObject {
        requests += 1
        return withContext(Dispatchers.IO) {
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
    }
}
