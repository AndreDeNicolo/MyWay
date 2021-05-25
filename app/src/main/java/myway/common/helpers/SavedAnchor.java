package myway.common.helpers;


import java.io.Serializable;

//this class save taps information
public class SavedAnchor implements Serializable {
    private float xcoord;
    private float ycoord;
    private long  downTime;
    private long eventTime;
    private int action;
    private int metaState;
    private float[] modelMatrix = new float[16];

    public SavedAnchor(float xcoord, float ycoord, long downTime, long eventTime, int action, int metaState, float[] modelMatrix) {
        this.xcoord = xcoord;
        this.ycoord = ycoord;
        this.downTime = downTime;
        this.eventTime = eventTime;
        this.action = action;
        this.metaState = metaState;
        this.modelMatrix = modelMatrix;
    }

    public float getXcoord() {
        return xcoord;
    }

    public float getYcoord() {
        return ycoord;
    }

    public long getDownTime() {
        return downTime;
    }

    public long getEventTime() {
        return eventTime;
    }

    public int getAction() {
        return action;
    }

    public int getMetaState() {
        return metaState;
    }

    public float[] getModelMatrix(){ return modelMatrix; }
}
