package util.plot;

import java.awt.Component;
import java.awt.Graphics;
import javax.swing.OverlayLayout;

public class PlotPane extends TransparentPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3614256696574188825L;
	private Plot<?> plot;
	
	
	public PlotPane(Plot<?> plot) { 
		this.plot = plot;
		setLayout(new OverlayLayout(this));
	}
	
	public Plot<?> getPlot() { return plot; }
	
	public PlotArea<?> getPlotArea() { return plot.getPlotArea(); }
	
	
	@Override
	public void paintComponent(Graphics g) {
		for(Component c : getComponents()) c.setSize(getSize());
		super.paintComponent(g);
	}
	
	
}
