package com.esri.arcgisruntime.sample.downloadpreplannedmap;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.widget.Toast;

import com.esri.arcgisruntime.ArcGISRuntimeException;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.FeatureTable;
import com.esri.arcgisruntime.layers.Layer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.portal.Portal;
import com.esri.arcgisruntime.portal.PortalItem;
import com.esri.arcgisruntime.tasks.offlinemap.DownloadPreplannedOfflineMapJob;
import com.esri.arcgisruntime.tasks.offlinemap.DownloadPreplannedOfflineMapResult;
import com.esri.arcgisruntime.tasks.offlinemap.OfflineMapTask;
import com.esri.arcgisruntime.tasks.offlinemap.PreplannedMapArea;

public class MainActivity extends AppCompatActivity {

  private MapView mMapView;

  private final String[] reqPermission = new String[] {
      Manifest.permission.WRITE_EXTERNAL_STORAGE
  };
  String TAG = MainActivity.class.getSimpleName();
  private ArrayList<PreplannedAreaPreview> mPreplannedAreaPreviews;
  private RecyclerView mRecyclerView;
  private OfflineMapTask mOfflineMapTask;
  private String mLocalPreplannedMapDir;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    //for API level 23+ request permission at runtime
    if (ContextCompat.checkSelfPermission(getApplicationContext(),
        reqPermission[0]) != PackageManager.PERMISSION_GRANTED) {
      //request permission
      int requestCode = 2;
      ActivityCompat.requestPermissions(MainActivity.this, reqPermission, requestCode);
    }

    mMapView = findViewById(R.id.mapView);
    mRecyclerView = findViewById(R.id.drawerRecyclerView);

    // define the local path where the preplanned map will be stored
    mLocalPreplannedMapDir = getCacheDir().toString() + File.separator + getString(R.string.file_name);
    Log.d(TAG, mLocalPreplannedMapDir);

    // create portal that contains the portal item

    //TODO - Update link with public data
    Portal portal = new Portal(getString(R.string.portal_url));

    // create webmap based on the id
    PortalItem webmapItem = new PortalItem(portal, getString(R.string.portal_item_ID));
    webmapItem.loadAsync();

    webmapItem.addDoneLoadingListener(() -> {
      if (webmapItem.getLoadStatus() == LoadStatus.NOT_LOADED) {
        Log.e(TAG, "Portal item failed to load: " + webmapItem.getLoadError().getMessage());
        return;
      }

      // create task and load it
      mOfflineMapTask = new OfflineMapTask(webmapItem);
      mOfflineMapTask.loadAsync();

      mOfflineMapTask.addDoneLoadingListener(() -> {
        if (mOfflineMapTask.getLoadStatus() == LoadStatus.NOT_LOADED) {
          Log.e(TAG, "Error loading OfflineMapTask: " + mOfflineMapTask.getLoadError().getMessage());
          return;
        }

        Log.d(TAG, "offline map task loaded");

        // get related preplanned areas
        ListenableFuture<List<PreplannedMapArea>> preplannedAreasFuture = mOfflineMapTask.getPreplannedMapAreasAsync();

        Log.d("prepAreaFuture", preplannedAreasFuture.toString());

        preplannedAreasFuture.addDoneListener(() -> {

          try {
            // get the list of preplanned map areas
            List<PreplannedMapArea> preplannedAreas = preplannedAreasFuture.get();

            mPreplannedAreaPreviews = new ArrayList<>();

            // load each area
            for (int i = 0; i < preplannedAreas.size(); i++) {
              PreplannedMapArea preplannedMapArea = preplannedAreas.get(i);
              preplannedMapArea.loadAsync();
              int finalI = i;
              preplannedMapArea.addDoneLoadingListener(() -> {

                PreplannedAreaPreview preview = new PreplannedAreaPreview();
                preview.setMapNum(finalI);
                preview.setTitle(String.valueOf(finalI + 1));
                preview.setThumbnailByteStream(preplannedMapArea.getPortalItem().getThumbnailData());
                mPreplannedAreaPreviews.add(preview);

                Log.d("mapNum", String.valueOf(preview.getMapNum()));

              });

            }

            DrawerAdapter adapter = new DrawerAdapter(mPreplannedAreaPreviews);
            mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            mRecyclerView.setAdapter(adapter);

          } catch (InterruptedException | ExecutionException e) {
            Toast.makeText(MainActivity.this, "Error loading preplanned areas: " + e.getMessage(), Toast.LENGTH_LONG)
                .show();
            Log.e(TAG, "Error loading preplanned areas: " + e.getMessage());
          }

        });
      });
    });

  }

  private void downloadMapAreaAsync(PreplannedMapArea mapArea) {
    // Setup UI for downloading
    //downloadNotificationText.Visibility = Visibility.Collapsed;
    //progressBar.IsIndeterminate = false;
    ///progressBar.Value = 0;
    //busyText.Text = "Downloading map area...";
    //busyIndicator.Visibility = Visibility.Visible;

    // Create folder path where it is downloaded
    String path = mLocalPreplannedMapDir + mapArea.getPortalItem().getTitle();

    // If area is already downloaded, just open it
    /*if (Directory.Exists(path))
    {
      var localMapArea = await MobileMapPackage.OpenAsync(path);
      MyMapView.Map = localMapArea.Maps.First();
      busyText.Text = string.Empty;
      busyIndicator.Visibility = Visibility.Collapsed;
      return;
    }*/

    // Create job that is used to do the download and hook the progress indication
    DownloadPreplannedOfflineMapJob downloadPreplannedOfflineMapJob = mOfflineMapTask
        .downloadPreplannedOfflineMap(mapArea, path);
    downloadPreplannedOfflineMapJob.start();
    //job.ProgressChanged += OnJobProgressChanged;
    downloadPreplannedOfflineMapJob.addJobDoneListener(() -> {

      // Download area and wait until it is fully downloaded
      DownloadPreplannedOfflineMapResult results = downloadPreplannedOfflineMapJob.getResult();

      // Handle possible errors and show them to the user
      if (results.hasErrors()) {
        StringBuilder errorBuilder = new StringBuilder();
        for (Map.Entry<Layer, ArcGISRuntimeException> layerError : results.getLayerErrors().entrySet()) {
          errorBuilder.append(layerError.getKey() + " " + layerError.getValue().getMessage());
        }
        for (Map.Entry<FeatureTable, ArcGISRuntimeException> tableError : results.getTableErrors().entrySet()) {
          errorBuilder.append(tableError.getKey() + " " + tableError.getValue().getMessage());
        }
        // Report error accessing a secured resource
        Log.e(TAG, String.valueOf(errorBuilder));
      }

      // Set the downloaded map to the view
      mMapView.setMap(results.getOfflineMap());

    });
  }
}
