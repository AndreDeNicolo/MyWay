package myway.common.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.myway.myway.R;

import java.io.File;

public class ScannerActivity extends Activity {

    private SurfaceView surfaceView;
    private CameraSource cameraSource;
    private TextView textView;
    private BarcodeDetector barcodeDetector;
    private Handler pathfoundhandler;

    private String previousPathScanned;//to not make many toast

    private String sessionType; //createway/findway
    private boolean detected; //allow only 1 detection

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        previousPathScanned = null;

        Bundle extras = getIntent().getExtras();
        sessionType = extras.getString("message");//does the previous activity call createway or findway?

        detected = false;
        initComponents();
        pathfoundhandler = new Handler(){
            @Override
            public void handleMessage(@NonNull Message msg) {
                if(((String)msg.obj).equals("notfound")){
                    notifyUser("Questo percorso non esiste");
                }
                else{
                    String pathfound = (String)msg.obj;
                    textView.setText(pathfound);
                }

            }
        };
    }

    private void initComponents(){
        textView = (TextView)findViewById(R.id.pathscanned);
        surfaceView = (SurfaceView)findViewById(R.id.camerareaderview);
        barcodeDetector = new BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.QR_CODE).build();
        cameraSource = new CameraSource.Builder(this, barcodeDetector)
                .setRequestedPreviewSize(650, 480).setFacing(CameraSource.CAMERA_FACING_BACK).build();

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (ActivityCompat.checkSelfPermission(ScannerActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                try{
                    cameraSource.start(holder);
                }catch (Exception e){
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                cameraSource.stop();
            }
        });
        barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
            @Override
            public void release() {

            }

            @Override
            public void receiveDetections(@NonNull Detector.Detections<Barcode> detections) {
                SparseArray<Barcode> qrCodes = detections.getDetectedItems();
                if(qrCodes.size() != 0 && !detected){
                    detected = true;
                    if(sessionType.equals(MainMenu.SESSION_CREATE)){
                        Log.i("sdsfds","hdhgddghgd");
                        Message m = Message.obtain();
                        String pathscanned = qrCodes.valueAt(0).displayValue;
                        m.obj = pathscanned;
                        m.setTarget(pathfoundhandler);
                        m.sendToTarget();

                        Intent i = new Intent(ScannerActivity.this, PopUpWindow.class);
                        startActivity(i);
                        /*Intent i = new Intent(ScannerActivity.this, IndoorNavSession.class);
                        String msg = MainMenu.SESSION_CREATE;
                        i.putExtra("sessiontype", msg);
                        i.putExtra("path",pathscanned);
                        startActivity(i);*/
                    }
                    else if(sessionType.equals(MainMenu.SESSION_FIND)){
                        String pathscanned = qrCodes.valueAt(0).displayValue;
                        String fileName = pathscanned;
                        ContextWrapper contextWrapper = new ContextWrapper(getApplicationContext());
                        File directory = contextWrapper.getDir(getFilesDir().getName(), Context.MODE_PRIVATE);
                        File arpathFound  = new File(directory, fileName);
                        if(!arpathFound.exists()){//notify user that pathscanned doesnt exist
                            //send to handlet notfound message to notify user by UI
                            if(!pathscanned.equals(previousPathScanned)){
                                Message m = Message.obtain();
                                String msg = "notfound";
                                m.obj = msg;
                                m.setTarget(pathfoundhandler);
                                m.sendToTarget();
                                previousPathScanned = pathscanned;
                            }
                        }//notify user that pathscanned doesnt exist
                        else {
                            Intent i = new Intent(ScannerActivity.this, IndoorNavSession.class);
                            String msg = MainMenu.SESSION_FIND;
                            i.putExtra("sessiontype", msg);
                            i.putExtra("path", pathscanned);
                            startActivity(i);
                        }
                    }

                }
            }
        });
    }

    private void notifyUser(String message){
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}