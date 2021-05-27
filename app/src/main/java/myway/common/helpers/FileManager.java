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

import myway.common.activities.IndoorNavSession;

//class used to load and save paths
public class FileManager {

    private Context context;
    private String qrCode;
    private String filePathName;

    public FileManager(Context context, String qrCode){//il qr code funziona come nome della cartella in cui poi saranno contenuti i percorsi salvati
        this.context = context;
        this.qrCode = qrCode;
    }

    //metodo per nominare il percorso che si vuole salvare e chiamare il metodo per il salvataggio
    public void savePath(ArrayList<SavedAnchor> savedAnchors){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Nome percorso creato");

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Salva", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                filePathName = (String)input.getText().toString();
                saveTrackedAnchorsPerQr(filePathName, savedAnchors);
                IndoorNavSession.notifyHandler(IndoorNavSession.HANDLER_WAHT_SAVED_MESSAGE);
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

    //metodo per salvare un percorso creato dall'utente (nome directory quella del qrcode)
    private void saveTrackedAnchorsPerQr(String fileName, ArrayList<SavedAnchor> savedAnchors){
        File qrDirectory = createQrDirectory();
        File outputFile = new File(qrDirectory, fileName);
        //saving path
        FileOutputStream fileOutputStream;
        ObjectOutputStream objectOutputStream;
        try{
            fileOutputStream = new FileOutputStream(outputFile.getAbsolutePath());
            objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(savedAnchors);//savedAnchors arraylist che contiene le ancore salvate
            objectOutputStream.close();
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //metodo per creare la directory associata ad un qr code (se questa non è mai stata creata)
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

    //metodo per caricare un percorso precedentemente salvato (ricarico l'arraylist di ancore)
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
