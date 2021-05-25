package myway.common.helpers;


import java.io.Serializable;

//this class save taps information
public class SavedAnchor implements Serializable {

    public static final int ANCHOR_TYPE_3D_OBJECT = 0;
    public static final int ANCHOR_TYPE_LABEL = 1;

    private int anchorType;
    //3d object attributes
    private float[] modelMatrix = new float[16];

    //label attributes
    private float[] viewProjectionMatrix = new float[16];
    private String label;


    //costruttore nel caso l'ancora salvata sia un oggetto 3D
    public SavedAnchor(float[] modelMatrix, int anchorType, String label) {
        this.anchorType = anchorType;
        this.modelMatrix = modelMatrix;
        if(anchorType == ANCHOR_TYPE_LABEL)this.label = label;
        else this.label = null;
    }

    //metodi oggetti 3D
    public float[] getModelMatrix(){ return modelMatrix; }

    //metodi label
    public int getAnchorType() {
        return anchorType;
    }

    public String getLabel(){
        return label;
    }
}
