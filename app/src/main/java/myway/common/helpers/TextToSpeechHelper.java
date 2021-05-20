package myway.common.helpers;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import java.util.Locale;

public class TextToSpeechHelper {

    private Context context;
    private TextToSpeech textToSpeech;

    public TextToSpeechHelper(Context context){
        this.context = context;
        init();
    }

    private void init(){
        textToSpeech = new TextToSpeech(context.getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status == TextToSpeech.SUCCESS) {
                    textToSpeech.setLanguage(Locale.ITALIAN);
                    textToSpeech.setPitch(1);
                }
            }
        });
    }

    public void speak(String s){
        textToSpeech.speak(s, TextToSpeech.QUEUE_FLUSH, null, "");
    }

    public void shutdown(){
        if(textToSpeech != null){
            textToSpeech.shutdown();
        }
    }

    public void stop(){
        if(textToSpeech != null){
            textToSpeech.stop();
        }
    }

}
