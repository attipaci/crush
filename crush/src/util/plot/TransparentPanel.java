package util.plot;

import java.awt.Graphics;

import javax.swing.JPanel;

public class TransparentPanel extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -464594201821733391L;
	private boolean transparent = false;
	
	public TransparentPanel() {}
	
	public boolean isTransparent() { return transparent; }
	
	public void setTransparent(boolean value) { transparent = value; }
	
	@Override
	public void paintComponent(Graphics g) {
		if(!transparent) super.paintComponent(g);
	}
}
