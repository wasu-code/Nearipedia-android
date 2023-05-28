package io.github.wasu_code.nearipedia

//import com.niels_ole.customtileserver.R

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.runBlocking
import org.osmdroid.config.Configuration.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
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
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {
    private lateinit var map : MapView
    private var languagecode = Locale.getDefault().language //code of language used when displaying articles
    private lateinit var overlay: ItemizedOverlayWithFocus<OverlayItem>
    private var searchRadius = 1000 //radious in meters to search for articles around given location
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //handle permissions first, before map is created.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(this as Activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        }

        //load/initialize the osmdroid configuration, this can be done
        // This won't work unless you have imported this: org.osmdroid.config.Configuration.*
        getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        //setting this before the layout is inflated is a good idea
        //it 'should' ensure that the map has a writable location for the map cache, even without permissions
        //if no tiles are displayed, you can try overriding the cache path using Configuration.getInstance().setCachePath
        //see also StorageUtils
        //note, the load method also sets the HTTP User Agent to your application's package name, if you abuse osm's
        //tile servers will get you banned based on this string.

        //inflate and create the map
        setContentView(R.layout.activity_main)

        //add bare map
        setupMap()
        //add overlays like +/- buttons, compass
        setupOverlays()


        ////Icons on the map with a click listener
        //your items
        val items = ArrayList<OverlayItem>()
        //Example use: items.add(OverlayItem("Title", "Description", GeoPoint(37.4288, -122.0811)))

        //GET articles for current user's location
        val currentLoc = getCurrentLocation(this)
        val articles = runBlocking {getNearbyArticles(currentLoc.first, currentLoc.second)}
        for (article in articles){
            items.add(OverlayItem(article.title, article.pageid.toString(), GeoPoint(article.lat, article.lon)))
        }

        //TODO set distance for article search


        //Web view and default massage when no article selected
        val webView: WebView = findViewById(R.id.webview)
        val helloMessage = resources.getString(R.string.hello_message)
        webView.loadData("<h2 style='text-align:center; margin: 4em auto; color: gray'>${helloMessage}</h2>", "text/html", "UTF-8")



        // setup the overlay with clickable points to open in webview
        updatePointsOverlay(items, webView)

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
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume() //needed for compass, my location overlays, v6.0.0 and up
    }

    override fun onPause() {
        super.onPause()
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
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

    private fun setupMap() {
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)

        val mapController = map.controller
        mapController.setZoom(16.5)
        val startPoint = GeoPoint(
            getCurrentLocation(this).first,
            getCurrentLocation(this).second
        )
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
        val retrofit = Retrofit.Builder()
            .baseUrl("https://${languagecode}.wikipedia.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val service = retrofit.create(WikipediaService::class.java)

        val response = service.getNearbyArticles("$latitude|$longitude", searchRadius)

        return response.query.geosearch
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

    private fun updatePointsOverlay(items: ArrayList<OverlayItem>, webView: WebView){
        overlay = ItemizedOverlayWithFocus<OverlayItem>(items, object:
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
        //map.invalidate() // Refresh the map view
    }

}