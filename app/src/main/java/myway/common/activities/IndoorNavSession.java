package myway.common.activities;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.Image;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Config.InstantPlacementMode;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;

import myway.common.helpers.DepthSettings;
import myway.common.helpers.DisplayRotationHelper;
import myway.common.helpers.FileManager;
import myway.common.helpers.FullScreenHelper;
import myway.common.helpers.InstantPlacementSettings;
import myway.common.helpers.OrientationHelper;
import myway.common.helpers.SavedTap;
import myway.common.helpers.SnackbarHelper;
import myway.common.helpers.TapHelper;
import myway.common.helpers.TrackingStateHelper;
import myway.common.samplerender.Framebuffer;
import myway.common.samplerender.GLError;
import myway.common.samplerender.Mesh;
import myway.common.samplerender.SampleRender;
import myway.common.samplerender.Shader;
import myway.common.samplerender.Texture;
import myway.common.samplerender.VertexBuffer;
import myway.common.samplerender.arcore.BackgroundRenderer;
import myway.common.samplerender.arcore.PlaneRenderer;
import myway.common.samplerender.arcore.SpecularCubemapFilter;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.myway.myway.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3D model.
 */
public class IndoorNavSession extends AppCompatActivity implements SampleRender.Renderer {

    //MINE CONSTANTS
    private static final float MODEL_SCALE = 1f;
    private static final float END_POINT_MODEL_SCALE = 0.01f;
    private static final float END_POINT_ROTATION_SPEED_DEGREES = 10;
    private static final float DIRECTION_ANGLE_ERROR = 15;//initial angle detected by the device is wrong by 20 degrees

    private static final String PLACE_MESSAGE = "Tocca una superficie per piazzare un indicatore di percorso.";
    private static final String FOLLOW_MESSAGE = "Segui il percorso indicato";

    // See the definition of updateSphericalHarmonicsCoefficients for an explanation of these
    // constants.
    //lighting management
    private static final float[] sphericalHarmonicFactors = {
            0.282095f,
            -0.325735f,
            0.325735f,
            -0.325735f,
            0.273137f,
            -0.273137f,
            0.078848f,
            -0.273137f,
            0.136569f,
    };

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100f;

    private static final int CUBEMAP_RESOLUTION = 16;
    private static final int CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32;

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView;

    private boolean installRequested;

    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private TapHelper tapHelper;
    private SampleRender render;

    private PlaneRenderer planeRenderer;
    private BackgroundRenderer backgroundRenderer;
    private Framebuffer virtualSceneFramebuffer;
    private boolean hasSetTextureNames = false;

    private final DepthSettings depthSettings = new DepthSettings();
    private boolean[] depthSettingsMenuDialogCheckboxes = new boolean[2];

    private final InstantPlacementSettings instantPlacementSettings = new InstantPlacementSettings();
    private boolean[] instantPlacementSettingsMenuDialogCheckboxes = new boolean[1];
    // Assumed distance from the device camera to the surface on which user will try to place objects.
    // This value affects the apparent scale of objects while the tracking method of the
    // Instant Placement point is SCREENSPACE_WITH_APPROXIMATE_DISTANCE.
    // Values in the [0.2, 2.0] meter range are a good choice for most AR experiences. Use lower
    // values for AR experiences where users are expected to place objects on surfaces close to the
    // camera. Use larger values for experiences where the user will likely be standing and trying to
    // place an object on the ground or floor in front of them.
    private static final float APPROXIMATE_DISTANCE_METERS = 2.0f;

    // Point Cloud
    private VertexBuffer pointCloudVertexBuffer;
    private Mesh pointCloudMesh;
    private Shader pointCloudShader;
    // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
    // was not changed.  Do this using the timestamp since we can't compare PointCloud objects.
    private long lastPointCloudTimestamp = 0;

    // Virtual object (ARCore pawn)
    private Mesh virtualObjectMesh;
    private Shader virtualObjectShader;
    private Mesh virtualObjectMeshEndPoint;
    private Shader virtualObjectShaderEndPoint;
    private final ArrayList<Anchor> anchors = new ArrayList<>();

    // Environmental HDR
    private Texture dfgTexture;
    private SpecularCubemapFilter cubemapFilter;

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private float[] modelMatrix = new float[16];
    private  float[] viewMatrix = new float[16];
    private  float[] projectionMatrix = new float[16];
    private  float[] modelViewMatrix = new float[16]; // view x model
    private  float[] modelViewProjectionMatrix = new float[16]; // projection x view x model
    private final float[] sphericalHarmonicsCoefficients = new float[9 * 3];
    private final float[] viewInverseMatrix = new float[16];
    private final float[] worldLightDirection = {0.0f, 0.0f, 0.0f, 0.0f};
    private final float[] viewLightDirection = new float[4]; // view x world light direction

    private final int MAX_3D_OBJECTS = 1000;
    private TextView cameraCoordTextView;
    private TextView deviceRotationTextView;
    private ArrayList<SavedTap> savedTaps = new ArrayList<>();
    private ArrayList<SavedTap> loadedTaps = new ArrayList<>();
    private boolean tapsLoaded;
    private double renderDistance;
    private Pose cameraPose;

    private String sessionType;
    private String qrDir;//string passed from previous activity (createway or findway)
    private String pathName;

    private Handler coordstextHandler;
    //gyroscope attributes
    private SensorManager sensorManager;

    private OrientationHelper orientationHelper;

    private FileManager fileManager;
    private double lastTime = System.currentTimeMillis();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_indoor_nav_session);
        surfaceView = findViewById(R.id.surfaceview);
        cameraCoordTextView = findViewById(R.id.cameracoordstext);
        cameraCoordTextView.setTextColor(Color.RED);
        deviceRotationTextView = findViewById(R.id.devicerotationtext);
        deviceRotationTextView.setTextColor(Color.GREEN);
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);
        initSensors();//initialize orientationHelper and sensorManager
        // Set up touch listener.
        tapsLoaded = false;
        renderDistance = 4;

        tapHelper = new TapHelper(/*context=*/ this);
        surfaceView.setOnTouchListener(tapHelper);

        // Set up renderer.
        render = new SampleRender(surfaceView, this, getAssets());

        installRequested = false;

        depthSettings.onCreate(this);
        instantPlacementSettings.onCreate(this);
        coordstextHandler = new Handler(){
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                //call to update textview with camera coords is not in the main thread so i have to handle the request in the main thread of the activity
                updateCameraCoordsTextView();
            }
        };

        instantPlacementSettings.setInstantPlacementEnabled(true);

        //get sessiontype info passed from previous activity
        Bundle extras = getIntent().getExtras();
        qrDir = extras.getString("qrCode");//qr code scanned
        pathName = extras.getString("pathName");
        sessionType = extras.getString("sessiontype");
        fileManager = new FileManager(this, qrDir);
        if(sessionType.equals(MainMenu.SESSION_FIND)){
            Toast.makeText(this, "Path caricata : "+ qrDir, Toast.LENGTH_SHORT).show();
            //loadTrackedAnchors();
            loadedTaps = fileManager.loadAnchors(qrDir, pathName);
        }


        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PopupMenu popup = new PopupMenu(IndoorNavSession.this, v);
                        popup.setOnMenuItemClickListener(IndoorNavSession.this::settingsMenuClick);
                        popup.inflate(R.menu.settings_menu);
                        popup.show();

                        if(sessionType.equals(MainMenu.SESSION_FIND))
                            hideMenuItems(popup.getMenu());
                    }
                });
    }

    //nascondere alcune voci del menù a seconda del tipo di sessione (se sto visualizzando un percorso salvato nascondo)
    private void hideMenuItems(Menu m){
        MenuItem depthsb = m.findItem(R.id.depth_settings);
        MenuItem undob = m.findItem(R.id.undo);
        MenuItem savePath = m.findItem(R.id.saveQrDir);

        depthsb.setVisible(false);
        undob.setVisible(false);
        savePath.setVisible(false);
    }

    //inizializzazione sensori
    private void initSensors(){
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        orientationHelper = new OrientationHelper();
    }

    //menu opzioni settaggio click
    protected boolean settingsMenuClick(MenuItem item) {
        if (item.getItemId() == R.id.depth_settings) {
            launchDepthSettingsMenuDialog();
            return true;
        }
        if(item.getItemId() == R.id.saveQrDir){
            fileManager.savePath(savedTaps);
            return true;
        }
        if(item.getItemId() == R.id.undo){
            if(!anchors.isEmpty()){
                anchors.remove(anchors.size()-1);
                savedTaps.remove(savedTaps.size()-1);
            }
        }
        if(item.getItemId() == R.id.distancesetting){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Set render anchors distance");

            // Set up the input
            final EditText input = new EditText(this);
            // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            builder.setView(input);

            // Set up the buttons
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    renderDistance = Double.parseDouble(input.getText().toString());

                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            builder.show();
            return true;
        }
        if(item.getItemId() == R.id.showinfo){
            if(cameraCoordTextView.getVisibility() == View.VISIBLE)
                cameraCoordTextView.setVisibility(View.INVISIBLE);
            else
                cameraCoordTextView.setVisibility(View.VISIBLE);
            return true;
        }
        if(item.getItemId() == R.id.exit){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Tornare al menù principale ? ");
            // Set up the buttons
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent i = new Intent(IndoorNavSession.this, MainMenu.class);
                    finish();  //Kill the activity from which you will go to next activity
                    startActivity(i);
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            builder.show();
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        sensorManager.unregisterListener(orientationHelper.getAccelerometerListener());
        sensorManager.unregisterListener(orientationHelper.getMagnetometerListener());
        if (session != null) {
            //guardare lifecycle
            //chiusura della sessione di arcore per rilasciare le risorse utilizzate
            session.close();
            session = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //registrazione sensori utilizzati
        sensorManager.registerListener(orientationHelper.getAccelerometerListener(), sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(orientationHelper.getMagnetometerListener(), sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_FASTEST);
        ///////
        //controllo se arcore risulta installato sul dispositivo
        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }
                // Create the session.
                session = new Session(/* context= */ this);
            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                return;
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            configureSession();
            // To record a live camera session for later playback, call
            // `session.startRecording(recorderConfig)` at anytime. To playback a previously recorded AR
            // session instead of using the live camera feed, call
            // `session.setPlaybackDataset(playbackDatasetPath)` before calling `session.resume()`. To
            // learn more about recording and playback, see:
            // https://developers.google.com/ar/develop/java/recording-and-playback
            session.resume();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            session = null;
            return;
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        //unregistrare sensori precedentemente registrati
        sensorManager.unregisterListener(orientationHelper.getAccelerometerListener());
        sensorManager.unregisterListener(orientationHelper.getMagnetometerListener());
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onSurfaceCreated(SampleRender render) {
        //inizializzazione degli oggetti da renderizzare. Include lettura degli shaders e dei modelli 3D
        try {
            planeRenderer = new PlaneRenderer(render);
            backgroundRenderer = new BackgroundRenderer(render);
            virtualSceneFramebuffer = new Framebuffer(render, /*width=*/ 1, /*height=*/ 1);

            cubemapFilter =
                    new SpecularCubemapFilter(
                            render, CUBEMAP_RESOLUTION, CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES);
            // Load DFG lookup table for environmental lighting
            dfgTexture =
                    new Texture(
                            render,
                            Texture.Target.TEXTURE_2D,
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            /*useMipmaps=*/ false);
            // The dfg.raw file is a raw half-float texture with two channels.
            final int dfgResolution = 64;
            final int dfgChannels = 2;
            final int halfFloatSize = 2;

            ByteBuffer buffer =
                    ByteBuffer.allocateDirect(dfgResolution * dfgResolution * dfgChannels * halfFloatSize);
            try (InputStream is = getAssets().open("models/dfg.raw")) {
                is.read(buffer.array());
            }
            // SampleRender abstraction leaks here.
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.getTextureId());
            GLError.maybeThrowGLException("Failed to bind DFG texture", "glBindTexture");
            GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D,
                    /*level=*/ 0,
                    GLES30.GL_RG16F,
                    /*width=*/ dfgResolution,
                    /*height=*/ dfgResolution,
                    /*border=*/ 0,
                    GLES30.GL_RG,
                    GLES30.GL_HALF_FLOAT,
                    buffer);
            GLError.maybeThrowGLException("Failed to populate DFG texture", "glTexImage2D");

            // Point cloud
            pointCloudShader =
                    Shader.createFromAssets(
                            render, "shaders/point_cloud.vert", "shaders/point_cloud.frag", /*defines=*/ null)
                            .setVec4(
                                    "u_Color", new float[] {31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f})
                            .setFloat("u_PointSize", 5.0f);
            // four entries per vertex: X, Y, Z, confidence
            pointCloudVertexBuffer =
                    new VertexBuffer(render, /*numberOfEntriesPerVertex=*/ 4, /*entries=*/ null);
            final VertexBuffer[] pointCloudVertexBuffers = {pointCloudVertexBuffer};
            pointCloudMesh =
                    new Mesh(
                            render, Mesh.PrimitiveMode.POINTS, /*indexBuffer=*/ null, pointCloudVertexBuffers);

            //virtual object to render end point
            Texture virtualObjectEndPointTexture =
                    Texture.createFromAsset(
                            render,
                            "models/endpointtexture.png",
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.SRGB);
            Texture virtualObjectPbrTextureEndPoint =
                    Texture.createFromAsset(
                            render,
                            "models/endpointtexture.png",
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.LINEAR);
            virtualObjectMeshEndPoint = Mesh.createFromAsset(render, "models/endpoint.obj");
            virtualObjectShaderEndPoint =
                    Shader.createFromAssets(
                            render,
                            "shaders/environmental_hdr.vert",
                            "shaders/environmental_hdr.frag",
                            /*defines=*/ new HashMap<String, String>() {
                                {
                                    put(
                                            "NUMBER_OF_MIPMAP_LEVELS",
                                            Integer.toString(cubemapFilter.getNumberOfMipmapLevels()));
                                }
                            })
                            .setTexture("u_AlbedoTexture", virtualObjectEndPointTexture)
                            .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTextureEndPoint)
                            .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
                            .setTexture("u_DfgTexture", dfgTexture);
            // Virtual object to render (ARCore pawn)
            Texture virtualObjectAlbedoTexture =
                    Texture.createFromAsset(
                            render,
                            "models/arrowtexture.png",
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.SRGB);
            Texture virtualObjectPbrTexture =
                    Texture.createFromAsset(
                            render,
                            "models/arrowtexture.png",
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.LINEAR);
            virtualObjectMesh = Mesh.createFromAsset(render, "models/arrownavigation.obj");
            virtualObjectShader =
                    Shader.createFromAssets(
                            render,
                            "shaders/environmental_hdr.vert",
                            "shaders/environmental_hdr.frag",
                            /*defines=*/ new HashMap<String, String>() {
                                {
                                    put(
                                            "NUMBER_OF_MIPMAP_LEVELS",
                                            Integer.toString(cubemapFilter.getNumberOfMipmapLevels()));
                                }
                            })
                            .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
                            .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTexture)
                            .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
                            .setTexture("u_DfgTexture", dfgTexture);
        } catch (IOException e) {
            messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
        }
    }

    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        virtualSceneFramebuffer.resize(width, height);
    }

    @Override
    public void onDrawFrame(SampleRender render) {
        if (session == null) {
            return;
        }

        // Texture names should only be set once on a GL thread unless they change. This is done during
        // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
        // initialized during the execution of onSurfaceCreated.
        if (!hasSetTextureNames) {
            session.setCameraTextureNames(
                    new int[] {backgroundRenderer.getCameraColorTexture().getTextureId()});
            hasSetTextureNames = true;
        }

        //avvisare la sessione ARCore che la  grandezza della view è cambiata quindi bisogna aggiustare la perspective matrix e il background video
        displayRotationHelper.updateSessionIfNeeded(session);

        /////////////angolo get orientation//////////////
        if((System.currentTimeMillis() - lastTime) > 1000 ){
            Log.i("ANGOLOOOOOOOOOOOOOOOOOO PITCHHHHHHHHH:", Float.toString(orientationHelper.getAzimuth()));
            lastTime = System.currentTimeMillis();
        }

        //ottengo il frame corrente dalla sessione ARCore.
        Frame frame;
        try {
            frame = session.update();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            return;
        }
        Camera camera = frame.getCamera();

        //aggiorno lo stato di BackgroundRenderer a seconda dei depthsettings
        try {
            backgroundRenderer.setUseDepthVisualization(
                    render, depthSettings.depthColorVisualizationEnabled());
            backgroundRenderer.setUseOcclusion(render, depthSettings.useDepthForOcclusion());
        } catch (IOException e) {
            messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
            return;
        }
        //BackgroundRenderer.updateDisplayGeometry deve essere chiamato per ogni frame per aggiornare le coordinate usate per disegnare l'immagine di background della camera
        backgroundRenderer.updateDisplayGeometry(frame);

        if (camera.getTrackingState() == TrackingState.TRACKING
                && (depthSettings.useDepthForOcclusion()
                || depthSettings.depthColorVisualizationEnabled())) {
            try (Image depthImage = frame.acquireDepthImage()) {
                backgroundRenderer.updateCameraDepthTexture(depthImage);
            } catch (NotYetAvailableException e) {
                // This normally means that depth data is not available yet. This is normal so we will not
                // spam the logcat with this.
            }
        }
        // Get projection matrix.
        camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);
        // Get camera matrix and draw.
        camera.getViewMatrix(viewMatrix, 0);
        // Handle one tap per frame.
        if(sessionType.equals(MainMenu.SESSION_CREATE)){//Se la sessione è utilizzata per creare un percorso utilizzo il metodo handleTap per gestire il piazzamento di un ancora
            handleTap(frame, camera);
        }

        //notifico l'handler della textview delle coordinate del mondo reale di aggiornarle
        updateMessageCameraCoords(camera);

        // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
        //mantenere lo schermo sbloccato mentra si tracka qualcosa, ma permettegli di sbloccarsi quando no
        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

        // Show a message based on whether tracking has failed, if planes are detected, and if the user
        // has placed any objects.
        //Mostro un messaggio a seconda della tipologia di sessione (creazione percorso o utilizzo percorso)
        String message = null;
        if(sessionType.equals(MainMenu.SESSION_CREATE))
            message = PLACE_MESSAGE;
        else{
            message = FOLLOW_MESSAGE;
        }
        if (message == null) {
            messageSnackbarHelper.hide(this);
        } else {
            messageSnackbarHelper.showMessage(this, message);
        }

        // -- Draw background

        if (frame.getTimestamp() != 0) {
            // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
            // drawing possible leftover data from previous sessions if the texture is reused.
            backgroundRenderer.drawBackground(render);
        }


        // Visualize tracked points.
        // Use try-with-resources to automatically release the point cloud.
        try (PointCloud pointCloud = frame.acquirePointCloud()) {
            if (pointCloud.getTimestamp() > lastPointCloudTimestamp) {
                pointCloudVertexBuffer.set(pointCloud.getPoints());
                lastPointCloudTimestamp = pointCloud.getTimestamp();
            }
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            pointCloudShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
            render.draw(pointCloudMesh, pointCloudShader);
        }
        // Update lighting parameters in the shader
        updateLightEstimation(frame.getLightEstimate(), viewMatrix);

        // Visualize anchors created by touch.
        render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);
        //if is "findway" session load path previously created
        if(!sessionType.equals(MainMenu.SESSION_CREATE) && !tapsLoaded){//if this session is note the creating path session and anchors not created yet
            loadTaps(frame, camera);
        }
        for (int i = 0; i < anchors.size(); i++) {
            if(sessionType.equals(MainMenu.SESSION_CREATE)){
                //carico la model matrix ottenuta dall'ancora che voglio renderizzare
                modelMatrix = savedTaps.get(i).getModelMatrix();
                //scalo il modello 3D alla grandezza desiderata
                Matrix.scaleM(modelMatrix, 0, MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
                //ottengo le matrici per la visualizzazione 3D
                Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
                Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
                virtualObjectShader.setMat4("u_ModelView", modelViewMatrix);
                virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
                render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer);
            }
            else{
                modelMatrix = loadedTaps.get(i).getModelMatrix();
                //calcolo la distanza della camera dall'ancora che devo renderizzare, a seconda della distanza decido se renderizzare e come
                double cameraAnchorDistance = distanceCameraAnchor(camera.getPose(), modelMatrix);
                if(cameraAnchorDistance < renderDistance){
                    if(i == anchors.size()-1){//ultima ancora salvata la visualizzo con il modello 3D del punto finale
                        Matrix.rotateM(modelMatrix,0, END_POINT_ROTATION_SPEED_DEGREES, 0, 1, 0);
                        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
                        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
                        virtualObjectShaderEndPoint.setMat4("u_ModelView", modelViewMatrix);
                        virtualObjectShaderEndPoint.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
                        render.draw(virtualObjectMeshEndPoint, virtualObjectShaderEndPoint, virtualSceneFramebuffer);
                    }
                    else {//ancore non finali
                        float[] resizedModelMatrix = new float[16];
                        resizedModelMatrix = modelMatrix.clone();//clono la matrice originale del modello 3D
                        float scale = 1f - (float)(cameraAnchorDistance/(renderDistance));//man mano che mi avvicino al modello 3D lo renderizzo più grande
                        if(cameraAnchorDistance > 0 && cameraAnchorDistance < 2)
                            Matrix.scaleM(resizedModelMatrix, 0, 1, 1, 1);
                        else
                            Matrix.scaleM(resizedModelMatrix, 0, scale, scale, scale);
                        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, resizedModelMatrix, 0);
                        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
                        // Update shader properties and draw
                        virtualObjectShader.setMat4("u_ModelView", modelViewMatrix);
                        virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
                        render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer);
                    }
                }

            }

        }

        //compongo la scena virtuale con il background della camera
        backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);
    }

    //per ogni frame gestisco un tap, intanto la frequenza dei tap è piu bassa del frame rate
    private void handleTap(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();
        if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
            List<HitResult> hitResultList;
            if (instantPlacementSettings.isInstantPlacementEnabled()) {
                hitResultList =
                        frame.hitTestInstantPlacement(tap.getX(), tap.getY(), APPROXIMATE_DISTANCE_METERS);
            } else {
                hitResultList = frame.hitTest(tap);
            }
            for (HitResult hit : hitResultList) {
                //se un punto di instant placemente è stato toccato creo un ancora.
                //numero massimo di ancore (per la gestione delle risorse
                if (anchors.size() >= MAX_3D_OBJECTS) {
                    anchors.get(0).detach();
                    anchors.remove(0);
                }

                //aggiungere un'Ancora indica ad ARCore che deve mantere traccia di questa posizione nello spazio
                anchors.add(hit.createAnchor());
                //estraggo da quest'ultima ancora la sua model matrix (posizione nel mondo reale)
                float[] mm = new float[16];
                anchors.get(anchors.size()-1).getPose().toMatrix(mm, 0);
                saveTapLocationAnchor(tap, mm);//salvo modelmatrix dell ancora

                // For devices that support the Depth API, shows a dialog to suggest enabling
                // depth-based occlusion. This dialog needs to be spawned on the UI thread.
                this.runOnUiThread(this::showOcclusionDialogIfNeeded);
                // Hits are sorted by depth. Consider only closest hit on a plane, Oriented Point, or
                // Instant Placement Point.
                break;
            }
        }
    }


    private void loadTaps(Frame frame, Camera camera){//load saved taps
        Log.i("MIOINFO LOADINGGGGGGGGGGGGGG", "TAPSSSSSSSSSSSSSSS");
        for(int i = 0; i < loadedTaps.size(); i++){//recreate taps
            MotionEvent tap = MotionEvent.obtain(loadedTaps.get(i).getDownTime(), loadedTaps.get(i).getEventTime(), loadedTaps.get(i).getAction(), loadedTaps.get(i).getXcoord(), loadedTaps.get(i).getYcoord(), loadedTaps.get(i).getMetaState());
            if (tap != null) {
                List<HitResult> hitResultList;
                hitResultList = frame.hitTestInstantPlacement(tap.getX(), tap.getY(), APPROXIMATE_DISTANCE_METERS);
                Log.i("MIOINFO N HIT", Float.toString(tap.getX()));
                for (HitResult hit : hitResultList) {
                    ////////////////////

                    // Adding an Anchor tells ARCore that it should track this position in
                    // space. This anchor is created on the Plane to place the 3D model
                    // in the correct position relative both to the world and to the plane.
                    //here i want to save the tap on a file so next i can recreate anchors
                    anchors.add(hit.createAnchor());
                    // For devices that support the Depth API, shows a dialog to suggest enabling
                    // depth-based occlusion. This dialog needs to be spawned on the UI thread.
                    this.runOnUiThread(this::showOcclusionDialogIfNeeded);
                    // Hits are sorted by depth. Consider only closest hit on a plane, Oriented Point, or
                    // Instant Placement Point.
                    break;

                }
            }
        }
        //ho caricato tutti i taps
        if(anchors.size() == loadedTaps.size()){
            tapsLoaded = !tapsLoaded;
            //scalo l ultima ancora (oggetto 3D finale)lo faccio qui così lo faccio solo una volta
            scale3DObjectEndPoint();//scale the last 3d anchor dimension
        }
    }

    private void showOcclusionDialogIfNeeded() {
        boolean isDepthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
        if (!depthSettings.shouldShowDepthEnableDialog() || !isDepthSupported) {
            return; // Don't need to show dialog.
        }

        // Asks the user whether they want to use depth-based occlusion.
        new AlertDialog.Builder(this)
                .setTitle(R.string.options_title_with_depth)
                .setMessage(R.string.depth_use_explanation)
                .setPositiveButton(
                        R.string.button_text_enable_depth,
                        (DialogInterface dialog, int which) -> {
                            depthSettings.setUseDepthForOcclusion(true);
                        })
                .setNegativeButton(
                        R.string.button_text_disable_depth,
                        (DialogInterface dialog, int which) -> {
                            depthSettings.setUseDepthForOcclusion(false);
                        })
                .show();
    }

    private void launchDepthSettingsMenuDialog() {
        // Retrieves the current settings to show in the checkboxes.
        resetSettingsMenuDialogCheckboxes();

        // Shows the dialog to the user.
        Resources resources = getResources();
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            // With depth support, the user can select visualization options.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.options_title_with_depth)
                    .setMultiChoiceItems(
                            resources.getStringArray(R.array.depth_options_array),
                            depthSettingsMenuDialogCheckboxes,
                            (DialogInterface dialog, int which, boolean isChecked) ->
                                    depthSettingsMenuDialogCheckboxes[which] = isChecked)
                    .setPositiveButton(
                            R.string.done,
                            (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                    .setNegativeButton(
                            android.R.string.cancel,
                            (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
                    .show();
        } else {
            // Without depth support, no settings are available.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.options_title_without_depth)
                    .setPositiveButton(
                            R.string.done,
                            (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                    .show();
        }
    }

    private void applySettingsMenuDialogCheckboxes() {
        depthSettings.setUseDepthForOcclusion(depthSettingsMenuDialogCheckboxes[0]);
        depthSettings.setDepthColorVisualizationEnabled(depthSettingsMenuDialogCheckboxes[1]);
        instantPlacementSettings.setInstantPlacementEnabled(
                instantPlacementSettingsMenuDialogCheckboxes[0]);
        configureSession();
    }

    private void resetSettingsMenuDialogCheckboxes() {
        depthSettingsMenuDialogCheckboxes[0] = depthSettings.useDepthForOcclusion();
        depthSettingsMenuDialogCheckboxes[1] = depthSettings.depthColorVisualizationEnabled();
        instantPlacementSettingsMenuDialogCheckboxes[0] =
                instantPlacementSettings.isInstantPlacementEnabled();
    }

    /** Update state based on the current frame's light estimation. */
    private void updateLightEstimation(LightEstimate lightEstimate, float[] viewMatrix) {
        if (lightEstimate.getState() != LightEstimate.State.VALID) {
            virtualObjectShader.setBool("u_LightEstimateIsValid", false);
            return;
        }
        virtualObjectShader.setBool("u_LightEstimateIsValid", true);

        Matrix.invertM(viewInverseMatrix, 0, viewMatrix, 0);
        virtualObjectShader.setMat4("u_ViewInverse", viewInverseMatrix);

        updateMainLight(
                lightEstimate.getEnvironmentalHdrMainLightDirection(),
                lightEstimate.getEnvironmentalHdrMainLightIntensity(),
                viewMatrix);
        updateSphericalHarmonicsCoefficients(
                lightEstimate.getEnvironmentalHdrAmbientSphericalHarmonics());
        cubemapFilter.update(lightEstimate.acquireEnvironmentalHdrCubeMap());
    }

    private void updateMainLight(float[] direction, float[] intensity, float[] viewMatrix) {
        // We need the direction in a vec4 with 0.0 as the final component to transform it to view space
        worldLightDirection[0] = direction[0];
        worldLightDirection[1] = direction[1];
        worldLightDirection[2] = direction[2];
        Matrix.multiplyMV(viewLightDirection, 0, viewMatrix, 0, worldLightDirection, 0);
        virtualObjectShader.setVec4("u_ViewLightDirection", viewLightDirection);
        virtualObjectShader.setVec3("u_LightIntensity", intensity);
    }

    private void updateSphericalHarmonicsCoefficients(float[] coefficients) {
        // Pre-multiply the spherical harmonics coefficients before passing them to the shader. The
        // constants in sphericalHarmonicFactors were derived from three terms:
        //
        // 1. The normalized spherical harmonics basis functions (y_lm)
        //
        // 2. The lambertian diffuse BRDF factor (1/pi)
        //
        // 3. A <cos> convolution. This is done to so that the resulting function outputs the irradiance
        // of all incoming light over a hemisphere for a given surface normal, which is what the shader
        // (environmental_hdr.frag) expects.
        //
        // You can read more details about the math here:
        // https://google.github.io/filament/Filament.html#annex/sphericalharmonics

        if (coefficients.length != 9 * 3) {
            throw new IllegalArgumentException(
                    "The given coefficients array must be of length 27 (3 components per 9 coefficients");
        }

        // Apply each factor to every component of each coefficient
        for (int i = 0; i < 9 * 3; ++i) {
            sphericalHarmonicsCoefficients[i] = coefficients[i] * sphericalHarmonicFactors[i / 3];
        }
        virtualObjectShader.setVec3Array(
                "u_SphericalHarmonicsCoefficients", sphericalHarmonicsCoefficients);
    }

    //configurazione della sessione ARCore
    private void configureSession() {
        Config config = session.getConfig();
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
        //non utilizzo il tracking dei piani però questo è un esempio su come indicare di trackare per esempio solo quelli orizzontali
        config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        } else {
            config.setDepthMode(Config.DepthMode.DISABLED);
        }
        if (instantPlacementSettings.isInstantPlacementEnabled()) {
            config.setInstantPlacementMode(InstantPlacementMode.LOCAL_Y_UP);
        } else {
            config.setInstantPlacementMode(InstantPlacementMode.DISABLED);
        }
        session.configure(config);
    }
    //method to save each anchor placement tap coors
    //example how to recreate a motionevent : https://www.generacodice.com/cn/articolo/1334664/Android%3A-How-to-create-a-MotionEvent
    private void saveTapLocationAnchor(MotionEvent tap, float[] newAnchorModelMatrix){
        float xcoord = tap.getX();
        float ycoord = tap.getY();
        long  downTime = tap.getDownTime();
        long eventTime = tap.getEventTime();
        int action = tap.getAction();
        int metaState = tap.getMetaState();
        //rotate matrix according to camera rotation
        //Matrix.rotateM(newAnchorModelMatrix, 0, -sensorAngle, 0, 1, 0);
        Matrix.rotateM(newAnchorModelMatrix, 0, -orientationHelper.getAzimuth() - DIRECTION_ANGLE_ERROR, 0, 1, 0);
        SavedTap ts = new SavedTap(xcoord, ycoord, downTime, eventTime, action, metaState, newAnchorModelMatrix);//save also the anchor model matrix = coords in the real world
        savedTaps.add(ts);
    }

    //metodo per calcolare distanza tra camera e un oggetto 3D
    private double distanceCameraAnchor(Pose cameraPose, float[] anchorModelMatrix){
        return Math.sqrt(Math.pow(cameraPose.tx()-anchorModelMatrix[12], 2)+ Math.pow(cameraPose.ty()-anchorModelMatrix[13], 2)+ Math.pow(cameraPose.tz()-anchorModelMatrix[14], 2));
    }

    private void updateCameraCoordsTextView(){
        cameraCoordTextView.setText("x: "+cameraPose.tx()+" y:"+cameraPose.ty()+" z:"+cameraPose.tz());
        deviceRotationTextView.setText("Device Rotation Roll : "+orientationHelper.getAzimuth());
    }

    //metodo per scalare l'ultima ancora (se la sessione e quella per seguire un percorso)
    private void scale3DObjectEndPoint(){
        Matrix.scaleM(
                loadedTaps.get(loadedTaps.size()-1).getModelMatrix(), 0, END_POINT_MODEL_SCALE, END_POINT_MODEL_SCALE, END_POINT_MODEL_SCALE);
    }

    //metodo per notificare l'handler di cambiare le coordinate della camera nel mondo reale a schermo (ImageVIew)
    private void updateMessageCameraCoords(Camera camera){
        cameraPose = camera.getPose();
        Message m = Message.obtain();
        m.what = 1;
        coordstextHandler.sendMessage(m);
    }
}
