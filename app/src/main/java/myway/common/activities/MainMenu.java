package myway.common.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import com.myway.myway.R;

import myway.common.helpers.CameraPermissionHelper;

public class MainMenu extends Activity {

    public static final String SESSION_CREATE = "createway";
    public static final String SESSION_FIND = "findway";

    private CardView cardViewCreateWay;
    private CardView cardViewFindWay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);
        cardViewCreateWay =findViewById(R.id.createway);
        cardViewFindWay = findViewById(R.id.findway);
        initListeners();
    }

    private void initListeners(){
        cardViewCreateWay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainMenu.this, ScannerActivity.class);
                String msg = SESSION_CREATE;
                i.putExtra("message", msg);
                startActivity(i);
            }
        });

        cardViewFindWay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainMenu.this, ScannerActivity.class);
                String msg = SESSION_FIND;
                i.putExtra("message", msg);
                startActivity(i);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        //ARCore necessita dei permessi per utilizzare la fotocamera
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this);
            return;
        }
    }

    //handle permissions request (camera)
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            // Use toast instead of snackbar here since the activity will exit.
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }
}