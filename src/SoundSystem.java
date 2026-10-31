import javax.sound.sampled.*;
import java.io.File;
import java.util.Random;

public class SoundSystem {

    private static final String RES="SneakySnakes/Resources/content/sounds/";
    private static final Random RNG=new Random();

    private static boolean enabled=true;
    private static Clip music;
    private static float volume=1.0f;

    public static void playOnce(String file){
        if(!enabled)return;
        try{
            AudioInputStream a=AudioSystem.getAudioInputStream(new File(RES+file));
            Clip c=AudioSystem.getClip();
            c.open(a);
            applyVolume(c);
            c.start();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void play(String sound){
        playOnce(sound+".wav");
    }

    public static void playClick(){
        playOnce("click.wav");
    }

    public static void playHit(){
        playOnce("hit.wav");
    }

    public static void startMusic(String file){
        stopMusic();
        if(!enabled)return;

        try{
            AudioInputStream a=AudioSystem.getAudioInputStream(new File(RES+file));
            music=AudioSystem.getClip();
            music.open(a);
            applyVolume(music);
            music.loop(Clip.LOOP_CONTINUOUSLY);
            music.start();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void startMenuMusic(){
        startMusic("background.wav");
    }

    public static void startRandomTrack(){
        startMusic("track"+(RNG.nextInt(4)+1)+".wav");
    }

    public static void stopMusic(){
        if(music!=null){
            music.stop();
            music.close();
            music=null;
        }
    }

    public static void setEnabled(boolean state){
        enabled=state;
        if(!enabled)stopMusic();
    }

    public static boolean isEnabled(){
        return enabled;
    }

    public static void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
        if (music != null && music.isOpen()) applyVolume(music);
    }

    private static void applyVolume(Clip c) {
        try {
            FloatControl fc = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = volume <= 0f ? fc.getMinimum() : 20f * (float)Math.log10(volume);
            fc.setValue(Math.max(fc.getMinimum(), Math.min(fc.getMaximum(), dB)));
        } catch (Exception ignored) {}
    }

}