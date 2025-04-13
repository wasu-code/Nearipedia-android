package io.github.wasu_code.nearipedia

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus
import org.osmdroid.views.overlay.OverlayItem
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale


class MainActivity : AppCompatActivity() {
    private lateinit var map : MapView
    private lateinit var webView: WebView
    private lateinit var slidingPaneLayout: SlidingPaneLayout
    private var languagecode = Locale.getDefault().language //code of language used when displaying articles
    private lateinit var overlay: ItemizedOverlayWithFocus<OverlayItem>
    private var searchRadius = 1000 //radious in meters to search for articles around given location

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestLocationPermission()
        initializeMapConfig()
        setContentView(R.layout.activity_main) //inflate and create the map
        initializeWebView()
        initializeSlidingPanel()
        setupMap()
        setupOverlays() //add overlays like +/- buttons, compass
        loadInitialArticles()//Icons on the map with a click listener
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search_here -> {
                // Handle location button click here
                showArticlesOnMapCenter()
                true
            }
            R.id.action_clear_points -> {
                reloadOverlay()
                true
            }
            R.id.action_change_language -> {
                showLanguageMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        //this will refresh the osmdroid configuration on resuming.
        map.onResume() //needed for compass, my location overlays, v6.0.0 and up
    }

    override fun onPause() {
        super.onPause()
        //this will refresh the osmdroid configuration on resuming.
        map.onPause()  //needed for compass, my location overlays, v6.0.0 and up
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted
        } else {
            // Permission denied
            Toast.makeText(
                this,
                getString(R.string.toast_location_permission),
                Toast.LENGTH_SHORT
            ).show()
            //finish()
        }

    }

    ////

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        }
    }

    private fun initializeMapConfig() {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        Configuration.getInstance().load(this, sharedPreferences)
    }

    private fun initializeWebView() {
        webView = findViewById(R.id.webview)
        val helloMessage = resources.getString(R.string.hello_message)
        webView.loadData(
            "<h2 style='text-align:center; margin: 2em auto; color: gray'>${helloMessage.replace("\n", "<br>")}</h2>",
            "text/html",
            "UTF-8"
        )
    }

    private fun loadInitialArticles() {
        lifecycleScope.launch {
            val currentLoc = getCurrentLocation(this@MainActivity)
            try {
                val articles = getNearbyArticles(currentLoc.first, currentLoc.second)
                val items = articles.map {
                    OverlayItem(it.title, it.pageid.toString(), GeoPoint(it.lat, it.lon))
                }
                updatePointsOverlay(items, webView)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Failed to load nearby articles", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }

        //TODO set distance for article search
    }

    private fun initializeSlidingPanel() {
        slidingPaneLayout = findViewById(R.id.sliding_panel_layout)
        val divider: View = findViewById(R.id.divider)
        divider.setOnTouchListener(ResizeTouchListener())
    }


    private fun setupMap() {
        map = findViewById(R.id.map)


//        // Custom TileSource URL
//        val customTileUrl = "https://a.tile.openstreetmap.de/{z}/{x}/{y}.png"
//        // Create a new TileSource using your custom URL
//        val customTileSource: ITileSource =
//            XYTileSource("OpenStreetMap.de", 0, 18, 256, ".png", arrayOf(customTileUrl))
//        map.setTileSource(customTileSource);

//        map.setTileSource(TileSourceFactory.MAPNIK)

        map.setTileSource(object : OnlineTileSourceBase(
            "OpenStreetMap.de", 0, 18, 256, "png",
            arrayOf<String>("https://a.tile.openstreetmap.de/")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val url = (baseUrl
                        + MapTileIndex.getZoom(pMapTileIndex)
                        + "/" + MapTileIndex.getX(pMapTileIndex)
                        + "/" + MapTileIndex.getY(pMapTileIndex)
                        + "." + mImageFilenameEnding)

                Log.i("MAP_EXAMPLE", url)

                return url
            }
        })

        val mapController = map.controller
        mapController.setZoom(16.5)

        val (lat, lon) = getCurrentLocation(this)
        val startPoint = GeoPoint(lat, lon)
        mapController.setCenter(startPoint)
    }

    private fun setupOverlays() {
        //User current location
        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        map.overlays.add(locationOverlay)

        //Compass overlay
        val compassOverlay = CompassOverlay(this, InternalCompassOrientationProvider(this), map)
        compassOverlay.enableCompass()
        map.overlays.add(compassOverlay)

        //rotation gestures
        val rotationGestureOverlay = RotationGestureOverlay(map)
        rotationGestureOverlay.isEnabled
        map.setMultiTouchControls(true)
        map.overlays.add(rotationGestureOverlay)
    }

    private fun getCurrentLocation(context: Context): Pair<Double, Double> {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        }
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) //returns null when no permissions - 0.0 will be returned instead(look below)
        val latitude: Double = location?.latitude ?: 0.0
        val longitude: Double = location?.longitude ?: 0.0
        return Pair(latitude, longitude)
    }

    private suspend fun getNearbyArticles(latitude: Double, longitude: Double): List<WikipediaArticle> {
        return try {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://${languagecode}.wikipedia.org/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val service = retrofit.create(WikipediaService::class.java)

            val response = service.getNearbyArticles("$latitude|$longitude", searchRadius)

            response.query.geosearch
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Failed to fetch nearby articles", Toast.LENGTH_SHORT).show()
            }
            emptyList()
        }
    }

    private fun showArticlesOnMapCenter() {
        val mapCenter = map.mapCenter
        val items = ArrayList<OverlayItem>()
        val articles = runBlocking {getNearbyArticles(mapCenter.latitude, mapCenter.longitude)}
        for (article in articles){
            items.add(OverlayItem(article.title, article.pageid.toString(), GeoPoint(article.lat, article.lon)))
        }
        val webView: WebView = findViewById(R.id.webview)
        drawRadiusIndicatingCircle()
        updatePointsOverlay(items, webView)
        //points are added, older are not deleted
    }

    private fun updatePointsOverlay(items: List<OverlayItem>, webView: WebView){
        overlay = ItemizedOverlayWithFocus(items, object:
            ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
            override fun onItemSingleTapUp(index:Int, item:OverlayItem):Boolean {
                webView.setInitialScale(1)
                webView.loadUrl("https://${languagecode}.wikipedia.org/?curid=${item.snippet}")
                return true
            }
            override fun onItemLongPress(index:Int, item:OverlayItem):Boolean {
                //do something on long pressing location marker
                //TODO add article to favorites
                return false
            }
        }, this)

        overlay.setFocusItemsOnTap(true)
        map.overlays.add(overlay)
    }

    private fun showLanguageMenu() {
        val anchorView = findViewById<View>(R.id.action_change_language)
        val popupMenu = PopupMenu(this, anchorView)
        popupMenu.inflate(R.menu.popup_menu)

        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.menu_system -> {
                    languagecode = Locale.getDefault().language
                    reloadOverlay()
                    true
                }
                R.id.menu_polish -> {
                    languagecode = "pl"
                    reloadOverlay()
                    true
                }
                R.id.menu_english -> {
                    languagecode = "en"
                    reloadOverlay()
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun reloadOverlay() {
        val temp = map.overlays.first() //preserve user's location overlay
        map.overlays.clear() //clear all overlays
        map.overlays.add(temp) //restores user's location; clearing and readding it may not work correctly
        setupOverlays() //restore rest of overlays
        showArticlesOnMapCenter()
    }

    private fun drawRadiusIndicatingCircle() {

        // Calculate the latitude and longitude for the center point of the circle
        val centerLatitude = map.mapCenter.latitude
        val centerLongitude = map.mapCenter.longitude

        // Calculate the coordinates for the circle using the center point and radius
        val radiusInMeters = searchRadius.toDouble()
        val strokeWidth = 4
        val strokeColor = 0x80808080

        val circlePoints = ArrayList<GeoPoint>()
        for (i in 0..360) {
            val point = GeoPoint(centerLatitude, centerLongitude).destinationPoint(radiusInMeters, i.toDouble())
            circlePoints.add(point)
        }

        // Create a Polygon or Polyline object using the circle points
        val circleOverlay = Polygon().apply {
            points = circlePoints
        }
        circleOverlay.outlinePaint.color = strokeColor.toInt()
        circleOverlay.outlinePaint.strokeWidth = strokeWidth.toFloat()
        circleOverlay.outlinePaint.style = Paint.Style.STROKE

        map.overlays.add(circleOverlay)
    }

    private inner class ResizeTouchListener : View.OnTouchListener {
        private var initialY: Float = 0f
        private var initialWeightMap: Float = 1f
        private var initialWeightWebView: Float = 1f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    initialWeightMap = (map.layoutParams as LinearLayout.LayoutParams).weight
                    initialWeightWebView = (webView.layoutParams as LinearLayout.LayoutParams).weight
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - initialY
                    val height = slidingPaneLayout.height.toFloat()
                    val newWeightMap = initialWeightMap + deltaY / height
                    val newWeightWebView = initialWeightWebView - deltaY / height

                    if (newWeightMap > 0 && newWeightWebView > 0) {
                        (map.layoutParams as LinearLayout.LayoutParams).weight = newWeightMap
                        (webView.layoutParams as LinearLayout.LayoutParams).weight = newWeightWebView
                        map.requestLayout()
                        webView.requestLayout()
                    }
                    return true
                }
            }
            return false
        }
    }

}