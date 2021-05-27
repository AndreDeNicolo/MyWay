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

    private static final int SCANNER_HANDLER_WHAT_PATH_NOT_FOUND = 2;

    private SurfaceView surfaceView;
    private CameraSource cameraSource;
    private TextView textView;
    private BarcodeDetector barcodeDetector;
    private Handler scannerHandler;

    private String previousPathScanned;//to not make many toast

    private String sessionType; //createway/findway
    public static boolean detected; //allow only 1 detection, can be activated outside this class (PopUpWindow)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        previousPathScanned = null;

        Bundle extras = getIntent().getExtras();
        sessionType = extras.getString("message");//does the previous activity call createway or findway?

        detected = false;
        initComponents();
        scannerHandler = new Handler(){
            @Override
            public void handleMessage(@NonNull Message msg) {
                if(msg.what == SCANNER_HANDLER_WHAT_PATH_NOT_FOUND){
                    Toast.makeText(ScannerActivity.this, "IL QR CODE SCANNERIZZATO NON E' ASSOCIATO A NESSUN PERCORSO", Toast.LENGTH_SHORT).show();

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
                .setRequestedPreviewSize(650, 480).setFacing(CameraSource.CAMERA_FACING_BACK).setAutoFocusEnabled(true).build();

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
                    if(sessionType.equals(MainMenu.SESSION_CREATE)){
                        String qrCodeScanned = qrCodes.valueAt(0).displayValue;
                        //start creating path arcore session
                        Intent i = new Intent(ScannerActivity.this, IndoorNavSession.class);
                        String msg = MainMenu.SESSION_CREATE;
                        i.putExtra("sessiontype", msg);
                        i.putExtra("qrCode",qrCodeScanned);
                        startActivity(i);
                    }
                    else if(sessionType.equals(MainMenu.SESSION_FIND)){
                        //start pop up window, which contains the list of the files within qr directory
                        String qrCodeScanned = qrCodes.valueAt(0).displayValue;
                        if(qrCodeDirectoryExists(qrCodeScanned)){
                            Intent i = new Intent(ScannerActivity.this, PopUpWindow.class);
                            i.putExtra("qrCode", qrCodeScanned);
                            startActivity(i);
                            detected = true;
                        }
                        else{
                            notifyHandler(SCANNER_HANDLER_WHAT_PATH_NOT_FOUND);
                        }
                    }

                }
            }
        });
    }

    private void notifyHandler(int what){
        Message m = Message.obtain();
        m.what = what;
        scannerHandler.sendMessage(m);
    }

    private boolean qrCodeDirectoryExists(String qrCode){
        ContextWrapper contextWrapper = new ContextWrapper(this.getApplicationContext());
        File appFilesDirectory = contextWrapper.getDir(this.getFilesDir().getName(), Context.MODE_PRIVATE);
        File qrDirectory = new File(appFilesDirectory, qrCode);
        if(!qrDirectory.exists()){
            return false;
        }
        return true;
    }

    private void notifyHandler(int what, Object obj){
        Message m = Message.obtain();
        m.what = what;
        m.obj = obj;
        scannerHandler.sendMessage(m);
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
    }
}