package myway.common.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.graphics.Point;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.google.android.gms.vision.text.Line;
import com.myway.myway.R;

public class PopUpWindow extends Activity {

    private int screenWidth, screenHeight;
    private LinearLayout linearLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.getActionBar().setTitle("Seleziona percorso");
        setContentView(R.layout.activity_pop_up_window);

        setWindowLayout();
        setPopUpWindowProp();
        addPathButton("dasfjnasfa");
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

    public void addPathButton(String pathName){
        Button b = new Button(this);
        b.setText(pathName);
        linearLayout.addView(b);
    }
}