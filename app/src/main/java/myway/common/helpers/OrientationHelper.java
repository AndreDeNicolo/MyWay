package myway.common.helpers;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.widget.Toast;

public class OrientationHelper {

    private float[] mGravity;
    private float[] mGeomagnetic;
    private float[] rotationMatrix;
    private float[] orientation;
    private float[] iMat;
    private float angle_azimuth;
    private float angle_pitch;
    private float angle_roll;
    private float filtered_angle_azimuth;
    private float filtered_angle_pitch;
    private float filtered_angle_roll;
    private SensorEventListener accelerometerListener;
    private SensorEventListener magnetometerListener;


    public OrientationHelper(){
        mGravity = null;
        mGeomagnetic = null;
        rotationMatrix = new float[9];
        iMat = new float[9];
        orientation = new float[3];
        filtered_angle_azimuth = 0;
        filtered_angle_pitch = 0;
        filtered_angle_roll = 0;

        initListeners();
    }

    private void initListeners(){
        accelerometerListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
                    mGravity = event.values.clone();
                    processSensorData();
                }
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
        magnetometerListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
                    mGeomagnetic = event.values.clone();
                    processSensorData();
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };
    }

    private void processSensorData(){
        if(mGravity != null && mGeomagnetic != null){
            SensorManager.getRotationMatrix(rotationMatrix, iMat, mGravity, mGeomagnetic);
            SensorManager.getOrientation(rotationMatrix, orientation);
            angle_azimuth = (float)Math.toDegrees((double)orientation[0]); // orientation contains: azimut, pitch and roll
            angle_pitch = (float)Math.toDegrees((double)orientation[1]);
            angle_roll = (float)Math.toDegrees((double)orientation[2]);
            filtered_angle_azimuth = calculateFilteredAngle(angle_azimuth, filtered_angle_azimuth);
            filtered_angle_pitch = calculateFilteredAngle(angle_pitch, filtered_angle_pitch);
            filtered_angle_roll = calculateFilteredAngle(angle_roll, filtered_angle_roll);
            mGravity = null;
            mGeomagnetic = null;
        }
    }

    private float restrictAngle(float tmpAngle){
        while(tmpAngle>=180) tmpAngle-=360;
        while(tmpAngle<-180) tmpAngle+=360;
        return tmpAngle;
    }

    private float calculateFilteredAngle(float x, float y){
        final float alpha = 0.3f;
        float diff = x-y;

        //here, we ensure that abs(diff)<=180
        diff = restrictAngle(diff);

        y += alpha*diff;
        //ensure that y stays within [-180, 180[ bounds
        y = restrictAngle(y);

        return y;
    }

    public SensorEventListener getAccelerometerListener(){
        return accelerometerListener;
    }

    public SensorEventListener getMagnetometerListener(){
        return magnetometerListener;
    }

    public void resetAngles(){
        angle_roll = 0;
        angle_pitch = 0;
        angle_azimuth = 0;
    }
    public float getAzimuth(){
        return filtered_angle_azimuth;
    }
    public float getPitch(){
        return filtered_angle_pitch;
    }

    public float getRoll(){
        return filtered_angle_roll;
    }
}
