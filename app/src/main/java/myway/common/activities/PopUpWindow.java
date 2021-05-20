package myway.common.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.google.android.gms.vision.text.Line;
import com.myway.myway.R;

import java.io.File;

public class PopUpWindow extends Activity {

    private int screenWidth, screenHeight;
    private LinearLayout linearLayout;
    private String qrDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.getActionBar().setTitle("Seleziona percorso");
        setContentView(R.layout.activity_pop_up_window);


        Bundle extras = getIntent().getExtras();
        qrDirectory = extras.getString("qrCode");

        setWindowLayout();
        setPopUpWindowProp();
        addPathButton();

    }

    private void setPopUpWindowProp(){
        //display screenSize
        Display display = getWindowManager().getDefaultDisplay();
        Point screenSize = new Point();
        display.getSize(screenSize);
        screenWidth = screenSize.x;
        screenHeight = screenSize.y;

        getWindow().setLayout((int)(screenWidth*.7), (int)(screenHeight*.7));
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.gravity = Gravity.CENTER;
        params.x = 0;
        params.y = -20;

        getWindow().setAttributes(params);
    }

    private void setWindowLayout(){
        linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams linearLayoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        this.addContentView(linearLayout, linearLayoutParams);
    }

    public void addPathButton(){
        //lettura file salvati
        ContextWrapper contextWrapper = new ContextWrapper(getApplicationContext());
        File appDirectory = contextWrapper.getDir(getFilesDir().getName(), Context.MODE_PRIVATE);
        File qrDir = new File(appDirectory, qrDirectory);
        File[] files = qrDir.listFiles();
        for(int k = 0; k < files.length; k++){
            //Creazione bottoni
            Button b = new Button(this);
            b.setText(files[k].getName());
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(PopUpWindow.this, IndoorNavSession.class);
                    String msg = MainMenu.SESSION_FIND;
                    i.putExtra("sessiontype", msg);
                    i.putExtra("qrCode", qrDirectory);
                    i.putExtra("pathName", b.getText());
                    startActivity(i);
                }
            });
            linearLayout.addView(b);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //set detected in ScannerActivity so another popup window can be opened
        ScannerActivity.detected = false;
    }
}