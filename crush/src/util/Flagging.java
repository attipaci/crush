package util;

public interface Flagging {

	public boolean isFlagged(int pattern);
	
	public boolean isUnflagged(int pattern);

	public boolean isFlagged();
	
	public boolean isUnflagged();
	
	public void flag(int pattern);
	
	public void unflag(int pattern);
	
	public void unflag();
	
}

