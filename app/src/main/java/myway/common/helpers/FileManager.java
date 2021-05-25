package myway.common.helpers;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

//class used to load and save paths
public class FileManager {

    private Context context;
    private String qrCode;
    private String filePathName;

    public FileManager(Context context, String qrCode){
        this.context = context;
        this.qrCode = qrCode;
    }

    //method to save path in a file, within the qr code directory
    public void savePath(ArrayList<SavedAnchor> savedAnchors){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Nome percorso creato");

        // Set up the input
        final EditText input = new EditText(context);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("Salva", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                filePathName = (String)input.getText().toString();
                saveTrackedAnchorsPerQr(filePathName, savedAnchors);
            }
        });
        builder.setNegativeButton("Indietro", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    //save path created in a directory associated with qr code scanned
    private void saveTrackedAnchorsPerQr(String fileName, ArrayList<SavedAnchor> savedAnchors){
        File qrDirectory = createQrDirectory();
        File outputFile = new File(qrDirectory, fileName);
        //saving path
        FileOutputStream fileOutputStream;
        ObjectOutputStream objectOutputStream;
        try{
            fileOutputStream = new FileOutputStream(outputFile.getAbsolutePath());
            objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(savedAnchors);//savedAnchors arratlist wich contains saved coord of the anchor taps
            objectOutputStream.close();
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //create directory associated to a qr code scanned
    private File createQrDirectory(){
        ContextWrapper contextWrapper = new ContextWrapper(context.getApplicationContext());
        File appFilesDirectory = contextWrapper.getDir(context.getFilesDir().getName(), Context.MODE_PRIVATE);
        File qrDirectory = new File(appFilesDirectory, qrCode);
        if (!qrDirectory.exists())
        {
            qrDirectory.mkdirs();
        }
        return qrDirectory;
    }

    //load anchors saved previously in qr folder
    public ArrayList<SavedAnchor> loadAnchors(String qrDir, String pathName){
        ArrayList<SavedAnchor> loadedTaps = new ArrayList<>();

        ObjectInputStream objectInputStream;
        ContextWrapper contextWrapper = new ContextWrapper(context.getApplicationContext());
        File appDirectory = contextWrapper.getDir(context.getFilesDir().getName(), Context.MODE_PRIVATE);
        File qrDirectory = new File(appDirectory, qrDir);
        File inputFile  = new File(qrDirectory, pathName);
        try {
            FileInputStream fileInputStream = new FileInputStream(inputFile);
            objectInputStream = new ObjectInputStream(fileInputStream);
            loadedTaps = (ArrayList<SavedAnchor>) objectInputStream.readObject();
            Log.i("MIOINFO TAPS CARICATI", Integer.toString(loadedTaps.size()));
            objectInputStream.close();
            fileInputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return loadedTaps;
    }
}
