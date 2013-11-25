package utils;

public class LoadException extends Exception {

	private static final long serialVersionUID = 1L;

	public LoadException(String message) {
		super(message);
	}
	
	public static void assertion(boolean cond, String err) throws LoadException {
		if(!cond)
			throw new LoadException(err);
	}
	
}
