import gui.forms.GUIMain;
import gui.forms.GUIUpdate;
import thread.ExceptionHandler;
import util.settings.Settings;

import javax.swing.*;
import java.awt.*;

public class Boot {
    public static void main(final String[] args) {
        /* Thread-safe initialization */
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                setLookAndFeel();
                GUIMain g = new GUIMain();
                g.setVisible(true);
                if (GUIUpdate.checkForUpdate()) {
                    GUIUpdate gu = new GUIUpdate();
                    gu.setVisible(true);
                }
            }

            /**
             * Tries to set the swing look and feel.
             * All relevant exceptions are caught.
             */
            private void setLookAndFeel() {
                try {
                    Settings.LAF.load();
                    UIManager.setLookAndFeel(Settings.lookAndFeel);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}