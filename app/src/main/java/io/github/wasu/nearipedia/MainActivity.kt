package io.github.wasu.nearipedia
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.webkit.WebView

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
//import com.niels_ole.customtileserver.R

import org.osmdroid.config.Configuration.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus
import org.osmdroid.views.overlay.OverlayItem
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

import java.util.ArrayList

class MainActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    private lateinit var map : MapView
    private var countrycode = "pl"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //handle permissions first, before map is created. not depicted here

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

        map = findViewById<MapView>(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)

        val mapController = map.controller
        mapController.setZoom(16.5)
        val startPoint = GeoPoint(getCurrentLocation(this).first, getCurrentLocation(this).second) //GeoPoint(48.8583, 2.2944);
        mapController.setCenter(startPoint);

        //User current location
        var locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map);
        //this.mLocationOverlay.enableMyLocation(); //nie wiem co to robi ale bez tego też działa
        map.overlays.add(locationOverlay)

        //Compass overlay TODO to test
        var compassOverlay = CompassOverlay(this, InternalCompassOrientationProvider(this), map)
        compassOverlay.enableCompass()
        map.overlays.add(compassOverlay)

        //rotation gestures TODO to test
        val rotationGestureOverlay = RotationGestureOverlay(map)
        rotationGestureOverlay.isEnabled
        map.setMultiTouchControls(true)
        map.overlays.add(rotationGestureOverlay)

        ////Icons on the map with a click listener
        //your items
        val items = ArrayList<OverlayItem>()
        //items.add(OverlayItem("Title", "Description", GeoPoint(37.4288, -122.0811)))
        //GET articles
        val currentLoc = getCurrentLocation(this)
        val articles = runBlocking {getNearbyArticles(currentLoc.first, currentLoc.second)}
        for (article in articles){
            items.add(OverlayItem(article.title, article.pageid.toString(), GeoPoint(article.lat, article.lon)))
        }
        //TODO function to fetch articles for new location on click
        //TODO set distance for article search


        //the overlay
        var overlay = ItemizedOverlayWithFocus<OverlayItem>(items, object:
            ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
            override fun onItemSingleTapUp(index:Int, item:OverlayItem):Boolean {
                //do something
                val webView: WebView = findViewById(R.id.webview)
                webView.setInitialScale(1)
                webView.loadUrl("https://${countrycode}.wikipedia.org/?curid=${item.snippet}")
                return true
            }
            override fun onItemLongPress(index:Int, item:OverlayItem):Boolean {

                /*val webView: WebView = findViewById(R.id.webview)
                webView.loadUrl("https://en.wikipedia.org/?curid=${item.snippet}")*/


                return false
            }
        }, this)
        overlay.setFocusItemsOnTap(true);
        map.overlays.add(overlay);



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
        val permissionsToRequest = ArrayList<String>()
        var i = 0
        while (i < grantResults.size) {
            permissionsToRequest.add(permissions[i])
            i++
        }
        if (permissionsToRequest.size > 0) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS_REQUEST_CODE)
        }
    }


    /*private fun requestPermissionsIfNecessary(String[] permissions) {
        val permissionsToRequest = ArrayList<String>();
        permissions.forEach { permission ->
        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            permissionsToRequest.add(permission);
        }
    }
        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }*/

    fun getCurrentLocation(context: Context): Pair<Double, Double> {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100) //100 - PERMISSION_REQUEST_LOCATION
            //return null - 0.0 will be returned instead(look below)
        }
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) //?: return null
        val latitude: Double = location?.latitude ?: 0.0
        val longitude: Double = location?.longitude ?: 0.0
        return Pair(latitude, longitude)
    }

    suspend fun getNearbyArticles(latitude: Double, longitude: Double): List<WikipediaArticle> {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://${countrycode}.wikipedia.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            //TODO set pl. or en. etc. depending on language of system
        val service = retrofit.create(WikipediaService::class.java)

        val response = service.getNearbyArticles("$latitude|$longitude")

        return response.query.geosearch
    }

}