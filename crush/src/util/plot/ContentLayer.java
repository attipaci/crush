package util.plot;

import util.Vector2D;

public abstract class ContentLayer extends PlotLayer {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5434391089909200423L;

	@Override
	public void defaults() {
		// TODO Auto-generated method stub

	}
	
	public abstract Vector2D getReferencePoint();
	
	public abstract Vector2D getPlotRanges();
	
	public abstract void initialize();

}
