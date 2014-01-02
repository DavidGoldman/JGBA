package cores;

public class KeyPad {

	public static final byte A = 0;
	public static final byte B = 1;
	public static final byte SELECT = 2;
	public static final byte START = 3;
	public static final byte RIGHT = 4;
	public static final byte LEFT = 5;
	public static final byte UP = 6;
	public static final byte DOWN = 7;
	public static final byte R = 8;
	public static final byte L = 9;
	
	public static String toString(byte key) {
		switch(key) {
		case A: return "A";
		case B: return "B";
		case SELECT: return "SELECT";
		case START: return "START";
		case RIGHT: return "RIGHT";
		case LEFT: return "LEFT";
		case UP: return "UP";
		case DOWN: return "DOWN";
		case R: return "R";
		case L: return "L";
		default: return "INVALID";
		}
	}
	
	
	//each bit: 0 - key down, 1 - key up
	private short keyStatus;
	//each bit: 0 - ignore, 1 - select
	private short keyInterrupt;
	
	private boolean keyIRQEnabled, irqTypeAND;
	
	public KeyPad() {
		keyStatus = 0x3FF;
	}
	
	public void pressKey(byte b) {
		keyStatus &= ~(1 << b);
		if (keyIRQEnabled)
			checkIRQ();
	}
	
	public void releaseKey(byte b) {
		keyStatus |= (1 << b);
		if(keyIRQEnabled)
			checkIRQ();
	}
	
	private void checkIRQ() {
		if (irqTypeAND) {
			if (((~keyStatus) & keyInterrupt & 0x3FF) == (keyInterrupt & 0x3FF)) //All buttons down
				; //TODO IRQ
		}
		else {
			if (((~keyStatus) & keyInterrupt & 0x3FF) != 0) //At least one button down
				; //TODO IRQ
		}
	}
	
}
