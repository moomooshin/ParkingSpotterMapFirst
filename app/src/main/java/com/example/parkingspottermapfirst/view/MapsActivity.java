package com.example.parkingspottermapfirst.view;

import android.Manifest;
import android.animation.Animator;
import android.arch.lifecycle.ViewModelProviders;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.widget.EditText;
import android.widget.Toast;

import com.example.parkingspottermapfirst.R;
import com.example.parkingspottermapfirst.model.SpotData;
import com.example.parkingspottermapfirst.viewmodel.MainViewModel;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, Animator.AnimatorListener {

    private static final String LOG_TAG = MapsActivity.class.getSimpleName();
    private static final int DEFAULT_ZOOM = 15;
    private static final double DEFAULT_LAT = 37.422, DEFAULT_LNG = -122.084;
    public static final int REQUEST_LOCATION_PERMISSIONS = 17;

    private double userLat = 0, userLng = 0, searchLat = 0, searchLng = 0;

    MainViewModel viewModel = null;

    private GoogleMap mMap;
    EditText etLocation;

    private boolean cardIsDown = true, animating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        initAnimation();
        initViewModel();
        initSearchButton();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void initSearchButton() {
        etLocation = findViewById(R.id.etLocation);
        findViewById(R.id.btnSearch).setOnClickListener(view -> {
            String address = etLocation.getText().toString();
            if (!address.isEmpty()) {
                Geocoder geocoder = new Geocoder(MapsActivity.this);
                try {
                    List<Address> allAddresses = geocoder.getFromLocationName(address, 5);
                    if (allAddresses == null || allAddresses.isEmpty()) {
                        throw new IOException("No addresses found!");
                    }
                    Address location = allAddresses.get(0);
                    searchLat = location.getLatitude();
                    searchLng = location.getLongitude();

                    searchForSpotsAtLocation();
                } catch (IOException e) {
                    Toast.makeText(MapsActivity.this,
                            "Unrecognized location: " + address, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }
        });
    }

    private void initAnimation() {
        CardView searchCardView = findViewById(R.id.searchCardView);
        findViewById(R.id.btnMoveCard).setOnClickListener(view -> {
            if (!animating) {
                int translateDist = cardIsDown ? -280 : 280;
                searchCardView.animate()
                        .translationYBy(translateDist)
                        .setListener(MapsActivity.this)
                        .start();
                view.animate().translationYBy(translateDist).start();

                cardIsDown = !cardIsDown;
                animating = true;
            }
        });
    }

    private void initViewModel() {
        viewModel = ViewModelProviders.of(this).get(MainViewModel.class);

        viewModel.initRetrofit();

        viewModel.getError().observe(this, s ->
                Toast.makeText(this, s, Toast.LENGTH_LONG).show());
        viewModel.getSpots().observe(this, list -> {
            if (list != null) {
                Map<Marker, SpotData> markerInfo = new HashMap<>();

                for (SpotData spot : list) {
                    LatLng location = new LatLng(
                            Double.parseDouble(spot.lat),
                            Double.parseDouble(spot.lng));

                    mMap.moveCamera(CameraUpdateFactory.newLatLng(location));
                    Marker marker = mMap.addMarker(new MarkerOptions()
                            .position(location)
                            .title(spot.name)
                            .snippet("ID: " + spot.id)
                            .icon(BitmapDescriptorFactory
                                    .defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                    markerInfo.put(marker, spot);
                }

                mMap.setInfoWindowAdapter(new SpotMarkerWindowAdapter(markerInfo));
            }
        });
    }

    private void searchForSpotsAtLocation() {
        viewModel.searchForSpots(searchLat, searchLng);
    }

    private void checkFineLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSIONS);
        } else {
            updateDeviceLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION_PERMISSIONS &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            updateDeviceLocation();
        }
    }

    private void updateDeviceLocation() {
        FusedLocationProviderClient locationProviderClient = LocationServices
                .getFusedLocationProviderClient(this);
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationProviderClient.getLastLocation().addOnSuccessListener(
                    this::onGetLocation);
        } else {
            setLocationAsDefault();
        }
    }

    private void setLocationAsDefault() {
        userLat = DEFAULT_LAT;
        userLng = DEFAULT_LNG;
    }

    private void onGetLocation(Location location) {
        if (location == null) {
            setLocationAsDefault();
        } else {
            userLat = location.getLatitude();
            userLng = location.getLongitude();
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        checkFineLocationPermissions();
    }

    @Override
    public void onAnimationStart(Animator animation) {

    }

    @Override
    public void onAnimationEnd(Animator animation) {
        animating = false;
    }

    @Override
    public void onAnimationCancel(Animator animation) {

    }

    @Override
    public void onAnimationRepeat(Animator animation) {

    }
}
