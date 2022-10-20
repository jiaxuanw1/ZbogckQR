public class InvalidQRException extends Exception {

    public InvalidQRException(String errorMessage) {
        super("Invalid QR reading! " + errorMessage);
    }

}
