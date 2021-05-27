package myway.common.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.media.Image;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.InputType;
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
import com.google.ar.core.InstantPlacementPoint;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;

import myway.common.helpers.DepthSettings;
import myway.common.helpers.DisplayRotationHelper;
import myway.common.helpers.FileManager;
import myway.common.helpers.FullScreenHelper;
import myway.common.helpers.InstantPlacementSettings;
import myway.common.helpers.SavedAnchor;
import myway.common.helpers.SnackbarHelper;
import myway.common.helpers.TapHelper;
import myway.common.helpers.TextToSpeechHelper;
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
import myway.common.samplerender.labelrender.LabelRender;

import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.myway.myway.R;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3D model.
 */
public class IndoorNavSession extends AppCompatActivity implements SampleRender.Renderer {

    //COSTANTI VARIE
    private static final float MODEL_SCALE = 1f;
    private static final float END_POINT_MODEL_SCALE = 0.01f;
    private static final float END_POINT_ROTATION_SPEED_DEGREES = 10;
    private static final float LABEL_SCALE = 5f;
    private static final double LABEL_SIGNAL_WARN_DISTANCE = 5f;
    private static final float LABEL_CORRECTION_ANGLE = -45f;//angolo di correzzione delle label(vengono piazzate girate di 45gradi)

    //ISTRUZIONI PER L'HANDLER
    public static final int HANDLER_WHAT_UPDATE_CAMERA_TEXT_COORDS = 1;
    public static final int HANDLER_WHAT_CALCULATED_DISTANCE = 2;
    public static final int HANDLER_WHAT_TURN_ON_LABEL_PLACEMENT_MESSAGE = 3;
    public static final int HANDLER_WHAT_TURN_OFF_LABEL_PLACEMENT_MESSAGE = 4;
    public static final int HANDLER_WAHT_SAVED_MESSAGE = 5;

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

    private static boolean LABEL_PLACEMENT_ON = false;
    private LabelRender labelRenderer;
    private String label;

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

    // oggetti 3D
    private Mesh virtualObjectMesh;
    private Shader virtualObjectShader;
    private Mesh virtualObjectMeshEndPoint;
    private Shader virtualObjectShaderEndPoint;
    private final ArrayList<Anchor> anchors = new ArrayList<>();

    // Environmental HDR
    private Texture dfgTexture;
    private SpecularCubemapFilter cubemapFilter;

    //Matrici temporanee allocate qui per non gravare piu avanti sulla stampa
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
    private TextView measureInfoTextView;
    private TextView labelPlacementTextView;
    private ArrayList<SavedAnchor> savedAnchors = new ArrayList<>();
    private ArrayList<SavedAnchor> loadedAnchors = new ArrayList<>();
    private double renderDistance;
    private Pose cameraPose;

    private String sessionType;//Stringa passata dall'attività precedente indicante il tipo di sessione chiamata (creazione o carica percorso)
    private String qrDir;//Stringa passata dall'attività precedente contenente directory in cui salvare eventuali percorsi (è il qr scannerizzato)
    private String pathName;

    public static Handler guiHandler;//handler per gestire varie richieste (costanti sopra)

    private TextToSpeechHelper textToSpeechHelper;//helper per gestire un oggetto TextToSpeech

    private FileManager fileManager;//oggetto per gestire il salvataggio su file e il caricamento da file dei percorsi

    private ImageButton undoButton;

    private boolean measuring;
    private ArrayList<Pose> measurePoints;//contiene le Pose nello spazio tra cui poi calcolare la distanza
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_indoor_nav_session);
        initGuiElements();
        setUpGuiHandler();
        // Set up touch listener.
        renderDistance = 4;

        tapHelper = new TapHelper(/*context=*/ this);
        surfaceView.setOnTouchListener(tapHelper);
        // Set up renderer.
        render = new SampleRender(surfaceView, this, getAssets());

        installRequested = false;

        depthSettings.onCreate(this);
        instantPlacementSettings.onCreate(this);
        instantPlacementSettings.setInstantPlacementEnabled(true);

        measuring = false;
        measurePoints = new ArrayList<>();

        //estraggo i messaggi passatti dall'attività precedente
        Bundle extras = getIntent().getExtras();
        qrDir = extras.getString("qrCode");//qr code scanned
        pathName = extras.getString("pathName");
        sessionType = extras.getString("sessiontype");
        fileManager = new FileManager(this, qrDir);
        if(sessionType.equals(MainMenu.SESSION_FIND)){
            Toast.makeText(this, "Path caricata : "+ qrDir, Toast.LENGTH_SHORT).show();
            loadedAnchors = fileManager.loadAnchors(qrDir, pathName);
            scale3DObjectEndPoint();//riduco le dimensioni dell'oggetto 3D end point
        }

        if(sessionType.equals(MainMenu.SESSION_FIND))undoButton.setVisibility(View.INVISIBLE);
        textToSpeechHelper = new TextToSpeechHelper(this);
    }



    private void initGuiElements(){
        surfaceView = findViewById(R.id.surfaceview);
        cameraCoordTextView = findViewById(R.id.cameracoordstext);
        cameraCoordTextView.setTextColor(Color.RED);
        measureInfoTextView = findViewById(R.id.measureinfo);
        measureInfoTextView.setTextColor(Color.RED);
        measureInfoTextView.setText("Misura Distanza : off");
        labelPlacementTextView = findViewById(R.id.labelPlaceminfo);
        labelPlacementTextView.setTextColor(Color.RED);
        labelPlacementTextView.setText("Piazzamento segnali : off");
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);
        undoButton = findViewById(R.id.undo_button);
        undoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!anchors.isEmpty()){
                    anchors.remove(anchors.size()-1);
                    savedAnchors.remove(savedAnchors.size()-1);
                }
            }
        });
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

    private void setUpGuiHandler(){
        guiHandler = new Handler(){
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                //call to update textview with camera coords is not in the main thread so i have to handle the request in the main thread of the activity
                if(msg.what == HANDLER_WHAT_UPDATE_CAMERA_TEXT_COORDS){
                    updateCameraCoordsTextView();
                }
                if(msg.what == HANDLER_WHAT_CALCULATED_DISTANCE){
                    DecimalFormat df = new DecimalFormat("0.000");
                    Toast.makeText(IndoorNavSession.this, "distanza tra i 2 punti : "+df.format((double)msg.obj), Toast.LENGTH_SHORT).show();
                }
                if(msg.what == HANDLER_WHAT_TURN_ON_LABEL_PLACEMENT_MESSAGE){
                    Toast.makeText(IndoorNavSession.this, "Clicca dove vuoi piazzare il segnale", Toast.LENGTH_SHORT).show();
                }
                if(msg.what == HANDLER_WHAT_TURN_OFF_LABEL_PLACEMENT_MESSAGE){
                    Toast.makeText(IndoorNavSession.this, "Piazzamento segnali disattivato", Toast.LENGTH_SHORT).show();
                }
                if(msg.what == HANDLER_WAHT_SAVED_MESSAGE){
                    Toast.makeText(IndoorNavSession.this, "Percorso salvato sotto il QRCode : "+qrDir, Toast.LENGTH_SHORT).show();
                }
            }
        };
    }
    //nascondere alcune voci del menù a seconda del tipo di sessione (se sto visualizzando un percorso salvato nascondo)
    private void hideMenuItems(Menu m){
        MenuItem depthsb = m.findItem(R.id.depth_settings);
        MenuItem savePath = m.findItem(R.id.saveQrDir);
        MenuItem placeLab = m.findItem(R.id.placeLabel);
        MenuItem measureB = m.findItem(R.id.measure);

        depthsb.setVisible(false);
        savePath.setVisible(false);
        placeLab.setVisible(false);
        measureB.setVisible(false);
    }

    //menu opzioni settaggio click
    protected boolean settingsMenuClick(MenuItem item) {
        if (item.getItemId() == R.id.depth_settings) {
            launchDepthSettingsMenuDialog();
            return true;
        }
        if(item.getItemId() == R.id.saveQrDir){
            fileManager.savePath(savedAnchors);
            return true;
        }
        if(item.getItemId() == R.id.distancesetting){
            DecimalFormat df = new DecimalFormat("#.00");
            textToSpeechHelper.speak(df.format(distanceCameraAnchor(cameraPose, loadedAnchors.get(loadedAnchors.size()-1).getModelMatrix()))+"metri al termine del percorso");
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Set render anchors distance");
            // Set up the input
            final EditText input = new EditText(this);
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
            if(cameraCoordTextView.getVisibility() == View.VISIBLE) {
                cameraCoordTextView.setVisibility(View.INVISIBLE);
                measureInfoTextView.setVisibility(View.INVISIBLE);
                labelPlacementTextView.setVisibility(View.INVISIBLE);
            }
            else{
                cameraCoordTextView.setVisibility(View.VISIBLE);
                measureInfoTextView.setVisibility(View.VISIBLE);
                labelPlacementTextView.setVisibility(View.VISIBLE);
            }
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
        if(item.getItemId() == R.id.measure){
            toggleMeasureMode();
        }
        if(item.getItemId() == R.id.speakPathLength){
            calculateSpeechTotalPathLength();
        }
        if(item.getItemId() == R.id.placeLabel){
            if(!LABEL_PLACEMENT_ON){
                LABEL_PLACEMENT_ON = true;
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Cosa segnalare?");

                // Set up the input
                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                builder.setView(input);

                // Set up the buttons
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        label = (String)input.getText().toString();
                        notifyHandler(HANDLER_WHAT_TURN_ON_LABEL_PLACEMENT_MESSAGE);
                        labelPlacementTextView.setText("Piazzamento segnali : on ("+label+")");
                        labelPlacementTextView.setTextColor(Color.GREEN);
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.show();
            }
            else{
                LABEL_PLACEMENT_ON = false;
                notifyHandler(HANDLER_WHAT_TURN_OFF_LABEL_PLACEMENT_MESSAGE);
                labelPlacementTextView.setText("Piazzamento segnali : off");
                labelPlacementTextView.setTextColor(Color.RED);

            }
        }
        return false;
    }

    public static void notifyHandler(int what){
        Message m = Message.obtain();
        m.what = what;
        guiHandler.sendMessage(m);
    }

    public static void notifyHandler(int what, Object obj){
        Message m = Message.obtain();
        m.what = what;
        m.obj = obj;
        guiHandler.sendMessage(m);
    }

    //metodo per notificare l'handler di cambiare le coordinate della camera nel mondo reale a schermo (ImageVIew)
    private void updateMessageCameraCoords(Camera camera){
        cameraPose = camera.getPose();
        notifyHandler(HANDLER_WHAT_UPDATE_CAMERA_TEXT_COORDS);
    }

    private void toggleMeasureMode(){
        if(measuring){
            measuring = false;
            measureInfoTextView.setTextColor(Color.RED);
            measurePoints.clear();
        }
        else{
            measuring = true;
            measureInfoTextView.setTextColor(Color.GREEN);
        }
    }

    private void addPointToMeasure(Pose pointPose){
        if(measurePoints.size() < 2)
            measurePoints.add(pointPose);
        if(measurePoints.size() == 2){
            double distance = calculateDistance(measurePoints.get(0), measurePoints.get(1));
            toggleMeasureMode();
            notifyHandler(HANDLER_WHAT_CALCULATED_DISTANCE, distance);
        }
    }

    //////////////////////////////////////////////
    //ARSESSION/ACTIVITYMANAGEMENT/OPEMGL STAFF
    /////////////////////////////////////////////
    @Override
    protected void onDestroy() {
        if (session != null) {
            //guardare lifecycle
            //chiusura della sessione di arcore per rilasciare le risorse utilizzate
            session.close();
            session = null;
        }

        super.onDestroy();
        textToSpeechHelper.shutdown();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
        textToSpeechHelper.stop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    //inizializzo modelli 3D vari e il renderer delle label
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
            virtualObjectMesh = Mesh.createFromAsset(render, "models/arrow.obj");
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

            //initialize the label renderer
            labelRenderer = new LabelRender();
            labelRenderer.onSurfaceCreated(render);

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

        //controllo distanze con eventuali segnali piazzati in creazione
        if(sessionType.equals(MainMenu.SESSION_FIND)){
            warnIfLabelIsNear(camera.getPose());
        }

        //il render dei punti trackati e dei piani trackati lo faccio solo se sono nella modalità creazione
        if(sessionType.equals(MainMenu.SESSION_CREATE)){
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

            planeRenderer.drawPlanes(
                    render,
                    session.getAllTrackables(Plane.class),
                    camera.getDisplayOrientedPose(),
                    projectionMatrix);
            // Update lighting parameters in the shader
        }
        updateLightEstimation(frame.getLightEstimate(), viewMatrix);

        render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);

        if(sessionType.equals(MainMenu.SESSION_CREATE)){//sessione di creazione percorso
            for (int i = 0; i < savedAnchors.size(); i++) {
                if(savedAnchors.get(i).getAnchorType() == SavedAnchor.ANCHOR_TYPE_LABEL){
                    modelMatrix = savedAnchors.get(i).getModelMatrix();
                    float[] scaledModelMatrix = new float[16];
                    scaledModelMatrix = modelMatrix.clone();
                    Matrix.scaleM(scaledModelMatrix, 0, LABEL_SCALE, LABEL_SCALE, LABEL_SCALE);
                    Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, scaledModelMatrix, 0);
                    Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
                    labelRenderer.draw(render, modelViewProjectionMatrix, savedAnchors.get(i).getLabel());
                }
                else{
                    //carico la model matrix ottenuta dall'ancora che voglio renderizzare
                    modelMatrix = savedAnchors.get(i).getModelMatrix();
                    //scalo il modello 3D alla grandezza desiderata
                    Matrix.scaleM(modelMatrix, 0, MODEL_SCALE , MODEL_SCALE, MODEL_SCALE);
                    //ottengo le matrici per la visualizzazione 3D
                    Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
                    Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
                    virtualObjectShader.setMat4("u_ModelView", modelViewMatrix);
                    virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
                    render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer);
                }
            }
        }
        else{//sessione percorso caricato
            for (int i = 0; i < loadedAnchors.size(); i++) {
                modelMatrix = loadedAnchors.get(i).getModelMatrix();
                //calcolo la distanza della camera dall'ancora che devo renderizzare, a seconda della distanza decido se renderizzare e come
                double cameraAnchorDistance = distanceCameraAnchor(camera.getPose(), modelMatrix);
                if(cameraAnchorDistance < renderDistance){
                    if(i == loadedAnchors.size()-1 && loadedAnchors.get(i).getAnchorType() == SavedAnchor.ANCHOR_TYPE_3D_OBJECT){//ultima ancora salvata la visualizzo con il modello 3D del punto finale
                        Matrix.rotateM(modelMatrix,0, END_POINT_ROTATION_SPEED_DEGREES, 0, 1, 0);
                        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
                        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
                        virtualObjectShaderEndPoint.setMat4("u_ModelView", modelViewMatrix);
                        virtualObjectShaderEndPoint.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
                        render.draw(virtualObjectMeshEndPoint, virtualObjectShaderEndPoint, virtualSceneFramebuffer);
                    }
                    else {//ancore non finali
                        if(loadedAnchors.get(i).getAnchorType() == SavedAnchor.ANCHOR_TYPE_LABEL){
                            float[] scaledModelMatrix = new float[16];
                            scaledModelMatrix = modelMatrix.clone();
                            Matrix.scaleM(scaledModelMatrix, 0, LABEL_SCALE, LABEL_SCALE, LABEL_SCALE);
                            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, scaledModelMatrix, 0);
                            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
                            labelRenderer.draw(render, modelViewProjectionMatrix, loadedAnchors.get(i).getLabel());
                        }
                        else{
                            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
                            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
                            virtualObjectShader.setMat4("u_ModelView", modelViewMatrix);
                            virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
                            render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer);
                        }
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
            hitResultList = frame.hitTest(tap);
            for (HitResult hit : hitResultList) {
                //if ARCore is tracking anchor points then place the anchor
                Trackable trackable = hit.getTrackable();
                if ((trackable instanceof Plane
                        && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                        && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
                        || (trackable instanceof Point
                        && ((Point) trackable).getOrientationMode()
                        == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
                        || (trackable instanceof InstantPlacementPoint)) {
                    if (anchors.size() >= MAX_3D_OBJECTS) {
                        anchors.get(0).detach();
                        anchors.remove(0);
                    }
                    //aggiungere un'Ancora indica ad ARCore che deve mantere traccia di questa posizione nello spazio
                    if(measuring == true){
                        addPointToMeasure(hit.createAnchor().getPose());
                    }
                    else{
                        anchors.add(hit.createAnchor());
                        //mm matrice model dell oggetto, rotat
                        float[] mm = new float[16];
                        //camRotationMatrix matrice contiene rotazione rotazione solo sull'asse y della camera
                        float[] camRotationMatrix = new float[16];
                        //matrice finale che andrò a salvare contiene moltiplicazione tra matrice model dell oggetto per la rotazione della camera, per dargli il giusto orientamento
                        float[] finalM = new float[16];
                        //quaternions per creare una matrice di rotazione estratti dalla Pose della camera e i quaternions x z w azzerati per lasciare solo quello y e creare matrice con makeRotation di rotazione solo attorno y
                        float[] rotQuaternions = new float[4];
                        rotQuaternions = camera.getPose().getRotationQuaternion();
                        rotQuaternions[0] = 0;
                        rotQuaternions[2] = 0;
                        rotQuaternions[3] = 0;
                        camera.getPose().makeRotation(rotQuaternions).toMatrix(camRotationMatrix, 0);
                        //estraggo da quest'ultima ancora piazzata la sua model matrix (posizione nel mondo reale)
                        anchors.get(anchors.size()-1).getPose().toMatrix(mm, 0);
                        Matrix.multiplyMM(finalM, 0, mm, 0, camRotationMatrix, 0);
                        if(LABEL_PLACEMENT_ON){
                            Matrix.rotateM(finalM, 0, LABEL_CORRECTION_ANGLE, 0, 1, 0);
                            saveAnchor(finalM, SavedAnchor.ANCHOR_TYPE_LABEL, label);
                        }
                        else{
                            saveAnchor(finalM, SavedAnchor.ANCHOR_TYPE_3D_OBJECT, null);//salvo modelmatrix dell ancora
                        }

                        // For devices that support the Depth API, shows a dialog to suggest enabling
                        // depth-based occlusion. This dialog needs to be spawned on the UI thread.
                        this.runOnUiThread(this::showOcclusionDialogIfNeeded);
                        // Hits are sorted by depth. Consider only closest hit on a plane, Oriented Point, or
                        // Instant Placement Point.
                        break;
                    }
                }
            }
        }
    }

    //metodo gia presente di helloar sample
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

    //metodo gia presente di helloar sample
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

    //metodo gia presente di helloar sample
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
    //metodo gia presente di helloar sample
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

    //metodo gia presente di helloar sample
    private void updateMainLight(float[] direction, float[] intensity, float[] viewMatrix) {
        // We need the direction in a vec4 with 0.0 as the final component to transform it to view space
        worldLightDirection[0] = direction[0];
        worldLightDirection[1] = direction[1];
        worldLightDirection[2] = direction[2];
        Matrix.multiplyMV(viewLightDirection, 0, viewMatrix, 0, worldLightDirection, 0);
        virtualObjectShader.setVec4("u_ViewLightDirection", viewLightDirection);
        virtualObjectShader.setVec3("u_LightIntensity", intensity);
    }

    //metodo gia presente di helloar sample
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
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);//realistic
        //config.setLightEstimationMode(Config.LightEstimationMode.AMBIENT_INTENSITY);
        //non utilizzo il tracking dei piani però questo è un esempio su come indicare di trackare per esempio solo quelli orizzontali
        config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
        config.setFocusMode(Config.FocusMode.AUTO);//autofocus with camera
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


    ///////////////////////////////////////////////
    //SAVE/LOAD ANCHORS/3DCALCULATION METHODS
    //////////////////////////////////////////////

    //method to save each anchor placement tap coors
    //example how to recreate a motionevent : https://www.generacodice.com/cn/articolo/1334664/Android%3A-How-to-create-a-MotionEvent
    private void saveAnchor(float[] newAnchorModelMatrix, int anchorType, String label){
        SavedAnchor ts = null;
        if(anchorType == SavedAnchor.ANCHOR_TYPE_LABEL){
            Matrix.rotateM(newAnchorModelMatrix, 0, 180, 0, 1, 0);
            ts = new SavedAnchor(newAnchorModelMatrix, SavedAnchor.ANCHOR_TYPE_LABEL, label);//salvo le coordinate della label
        }
        if(anchorType == SavedAnchor.ANCHOR_TYPE_3D_OBJECT){
            Matrix.rotateM(newAnchorModelMatrix, 0, 180, 0, 1, 0);
            ts = new SavedAnchor(newAnchorModelMatrix, SavedAnchor.ANCHOR_TYPE_3D_OBJECT, null);//salvo la modelMatrix del modello 3D
        }
        savedAnchors.add(ts);
    }

    //metodo per calcolare distanza tra camera e un oggetto 3D
    private double distanceCameraAnchor(Pose cameraPose, float[] anchorModelMatrix){
        return Math.sqrt(Math.pow(cameraPose.tx()-anchorModelMatrix[12], 2)+ Math.pow(cameraPose.ty()-anchorModelMatrix[13], 2)+ Math.pow(cameraPose.tz()-anchorModelMatrix[14], 2));
    }

    private double calculateDistance(Pose p1, Pose p2){
        return Math.sqrt(Math.pow(p1.tx()-p2.tx(), 2)+ Math.pow(p1.ty()-p2.ty(), 2)+ Math.pow(p1.tz()-p2.tz(), 2));
    }

    private double calculateDistanceMatrix(float[] m1, float[] m2){
        return Math.sqrt(Math.pow(m1[12]-m2[12], 2)+ Math.pow(m1[13]-m2[13], 2)+ Math.pow(m1[14]-m2[14], 2));
    }

    private void updateCameraCoordsTextView(){
        DecimalFormat df = new DecimalFormat("0.00");
        cameraCoordTextView.setText("x: "+df.format(cameraPose.tx())+" y:"+df.format(cameraPose.ty())+" z:"+df.format(cameraPose.tz()));
    }

    //metodo per scalare l'ultima ancora (se la sessione e quella per seguire un percorso)
    private void scale3DObjectEndPoint(){
        Matrix.scaleM(loadedAnchors.get(loadedAnchors.size()-1).getModelMatrix(), 0, END_POINT_MODEL_SCALE, END_POINT_MODEL_SCALE, END_POINT_MODEL_SCALE);
    }

    //calcolo della lunghezza totale del percorso per poi notificarla vocalmente
    private void calculateSpeechTotalPathLength(){
        DecimalFormat df = new DecimalFormat("0.00");
        double pathLength = 0;
        pathLength += distanceCameraAnchor(cameraPose, loadedAnchors.get(0).getModelMatrix());//distanza dall'utente alla prima ancora
        for(int i = 0; i < loadedAnchors.size()-1; i++){//calcolo distanza tra le varie ancore successive e le sommo
            if(loadedAnchors.get(i).getAnchorType() == SavedAnchor.ANCHOR_TYPE_3D_OBJECT)//sommo solo tra frecce e non segnali
                pathLength += calculateDistanceMatrix(loadedAnchors.get(i).getModelMatrix(), loadedAnchors.get(i+1).getModelMatrix());
        }
        textToSpeechHelper.speak("Lunghezza totale del percorso : "+df.format(pathLength)+" metri");
    }

    //metodo per avvisare l'utente che si sta avvicinando ad un segnale
    //chiamabile solo se si è caricato un percorso
    private void warnIfLabelIsNear(Pose cameraPose){//cameraPose rappresenta la posizione corrente dell' utente
        for(int i = 0; i < loadedAnchors.size(); i++){
            if(loadedAnchors.get(i).getAnchorType() == SavedAnchor.ANCHOR_TYPE_LABEL && !loadedAnchors.get(i).isWarned()) {//se l'ancora è un segnale
                double calculatedDistance = distanceCameraAnchor(cameraPose, loadedAnchors.get(i).getModelMatrix());
                if (calculatedDistance < LABEL_SIGNAL_WARN_DISTANCE) {
                    loadedAnchors.get(i).setWarned();
                    notifyHandler(HANDLER_WHAT_TURN_OFF_LABEL_PLACEMENT_MESSAGE);
                    DecimalFormat df = new DecimalFormat("0.00");
                    textToSpeechHelper.speak("Attenzione ostacolo di tipo " + loadedAnchors.get(i).getLabel() + " a "+df.format(calculatedDistance)+" metri");
                }
            }
        }
    }
}
