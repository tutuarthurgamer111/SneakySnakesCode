import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class SneakySnakes {
    private static boolean fullscreen = false;
    private static Rectangle windowedBounds;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("SneakySnakes");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setMinimumSize(new Dimension(480, 360));
            frame.setSize(960, 720);
            frame.setLocationRelativeTo(null);

            GamePanel gamePanel = new GamePanel();
            frame.add(gamePanel);

            GraphicsDevice device = GraphicsEnvironment
                .getLocalGraphicsEnvironment().getDefaultScreenDevice();

            frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "toggleFullscreen");
            frame.getRootPane().getActionMap().put("toggleFullscreen", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    if (!fullscreen) {
                        windowedBounds = frame.getBounds();
                        frame.dispose();
                        frame.setUndecorated(true);
                        frame.setVisible(true);
                        device.setFullScreenWindow(frame);
                        fullscreen = true;
                    } else {
                        device.setFullScreenWindow(null);
                        frame.dispose();
                        frame.setUndecorated(false);
                        frame.setVisible(true);
                        if (windowedBounds != null) frame.setBounds(windowedBounds);
                        else frame.setSize(960, 720);
                        fullscreen = false;
                    }
                    gamePanel.requestFocusInWindow();
                }
            });

            frame.setVisible(true);
            gamePanel.requestFocusInWindow();
        });
    }
}
