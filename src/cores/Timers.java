package cores;

public class Timers {

	private static final short[] PRESCALER_TABLE = { 1, 64, 256, 1024 };
	private static final int OVERFLOW = 0x10000;

	//Count-Up Timing cannot be used for timer 0 as it is the first timer
	private int t0Counter, t0Reload, t0Control, t0Precounter;
	private boolean t0Enabled, t0IRQ;
	private short t0Prescaler;

	private int t1Counter, t1Reload, t1Control, t1Precounter;
	private boolean t1Enabled, t1IRQ, t1CountUp;
	private short t1Prescaler;

	private int t2Counter, t2Reload, t2Control, t2Precounter;
	private boolean t2Enabled, t2IRQ, t2CountUp;
	private short t2Prescaler;

	private int t3Counter, t3Reload, t3Control, t3Precounter;
	private boolean t3Enabled, t3IRQ, t3CountUp;
	private short t3Prescaler;

	public Timers() {
		//Prescalers initialize to 1, everything else initializes to 0/false
		t0Prescaler = t1Prescaler = t2Prescaler = t3Prescaler = 1;
	}
	
	public void step(int clocks) {
		stepSound(clocks); //Timer 0 and 1 are special sound timers
		clockTimer2(clocks);
		clockTimer3(clocks);
	}
	
	private void stepSound(int clocks) {
		
	}

	private void clockTimer0(int clocks) {
		if (t0Enabled) {
			t0Precounter += clocks;
			while (t0Precounter >= t0Prescaler) { //It -could- overflow multiple times depending on reload
				int iterations = Math.min(t0Precounter / t0Prescaler, OVERFLOW - t0Counter);
				t0Precounter -= iterations * t0Prescaler;
				t0Counter += iterations;
				if (t0Counter >= OVERFLOW) { //OVERFLOW (should be equal)
					t0Counter = t0Reload;
					triggerTimer0();
					countUpTimer1();
				}
			}
		}
	}

	private void clockTimer1(int clocks) {
		if (t1Enabled && !t1CountUp) {
			t1Precounter += clocks;
			while (t1Precounter >= t1Prescaler) { //It -could- overflow multiple times depending on reload
				int iterations = Math.min(t1Precounter / t1Prescaler, OVERFLOW - t1Counter);
				t1Precounter -= iterations * t1Prescaler;
				t1Counter += iterations;
				if (t1Counter >= OVERFLOW) { //OVERFLOW (should be equal)
					t1Counter = t1Reload;
					triggerTimer1();
					countUpTimer2();
				}
			}
		}
	}

	private void clockTimer2(int clocks) {
		if (t2Enabled && !t2CountUp) {
			t2Precounter += clocks;
			while (t2Precounter >= t2Prescaler) { //It -could- overflow multiple times depending on reload
				int iterations = Math.min(t2Precounter / t2Prescaler, OVERFLOW - t2Counter);
				t2Precounter -= iterations * t2Prescaler;
				t2Counter += iterations;
				if (t2Counter >= OVERFLOW) { //OVERFLOW (should be equal)
					t2Counter = t2Reload;
					triggerTimer2();
					countUpTimer3();
				}
			}
		}
	}

	private void clockTimer3(int clocks) {
		if (t3Enabled && !t3CountUp) {
			t3Precounter += clocks;
			while (t3Precounter >= t3Prescaler) { //It -could- overflow multiple times depending on reload
				int iterations = Math.min(t3Precounter / t3Prescaler, OVERFLOW - t3Counter);
				t3Precounter -= iterations * t3Prescaler;
				t3Counter += iterations;
				if (t3Counter >= OVERFLOW) { //OVERFLOW (should be equal)
					t3Counter = t3Reload;
					triggerTimer3();
				}
			}
		}
	}

	private void triggerTimer0() {

	}

	private void triggerTimer1() {

	}
	
	private void triggerTimer2() {

	}
	
	private void triggerTimer3() {

	}

	private void countUpTimer1() {
		if (t1Enabled && t1CountUp && (++t1Counter) >= OVERFLOW) {
			t1Counter = t1Reload;
			triggerTimer1();
			countUpTimer2();
		}
	}

	private void countUpTimer2() {
		if (t2Enabled && t2CountUp && (++t2Counter) >= OVERFLOW) {
			t2Counter = t2Reload;
			triggerTimer2();
			countUpTimer3();
		}
	}
	
	private void countUpTimer3() {
		if (t3Enabled && t3CountUp && (++t3Counter) >= OVERFLOW) {
			t3Counter = t3Reload;
			triggerTimer3();
		}
	}

}
