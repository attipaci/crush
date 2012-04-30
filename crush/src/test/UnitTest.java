package test;

import util.PowerUnit;
import util.Symbol;
import util.Unit;

public class UnitTest {

	public static void main(String[] args) {
		Unit u = null;
		
		u = PowerUnit.get("aJ");
		System.err.println(u + " = " + u.value());
		
		u = PowerUnit.get("GA");
		System.err.println(u + " = " + u.value());
		
		u = PowerUnit.get("mg^2");
		System.err.println(u + " = " + u.value());
		
		u = PowerUnit.get(Symbol.Acircle + "");
		System.err.println(u + " = " + u.value());
		
	}
	
}
