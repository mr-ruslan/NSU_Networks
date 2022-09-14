package protocol;

public abstract class Response {

    public abstract void writeTo(java.io.OutputStream stream) throws java.io.IOException;

    public static Response readFrom(java.io.InputStream stream) throws java.io.IOException {
        switch (StreamUtil.readInt(stream)) {
            case Success.TAG:
                return Success.readFrom(stream);
            case Failure.TAG:
                return Failure.readFrom(stream);
            case Accept.TAG:
                return Accept.readFrom(stream);
            case Reject.TAG:
                return Reject.readFrom(stream);
            default:
                throw new java.io.IOException("Unexpected tag value");
        }
    }



    public static class Success extends Response {
        public static final int TAG = 101;

        private String fileName;

        public Success(String fileName) {
            this.fileName = fileName;
        }

        public static Success readFrom(java.io.InputStream stream) throws java.io.IOException {

            String fileName = StreamUtil.readString(stream);
            return new Success(fileName);
        }

        @Override
        public void writeTo(java.io.OutputStream stream) throws java.io.IOException {
            StreamUtil.writeInt(stream, TAG);
            StreamUtil.writeString(stream, fileName);

        }

        public String getFileName() {
            return fileName;
        }

    }



    public static class Failure extends Response {
        public static final int TAG = 102;

        public Failure() {
        }

        public static Failure readFrom(java.io.InputStream stream) throws java.io.IOException {

            return new Failure();
        }

        @Override
        public void writeTo(java.io.OutputStream stream) throws java.io.IOException {
            StreamUtil.writeInt(stream, TAG);
        }

    }



    public static class Accept extends Response {
        public static final int TAG = 1;

        public Accept() {
        }

        public static Accept readFrom(java.io.InputStream stream) throws java.io.IOException {

            return new Accept();
        }

        @Override
        public void writeTo(java.io.OutputStream stream) throws java.io.IOException {
            StreamUtil.writeInt(stream, TAG);
        }

    }



    public static class Reject extends Response {
        public static final int TAG = 2;
        public String error;

        public Reject(String error) {
            this.error = error;
        }

        public static Reject readFrom(java.io.InputStream stream) throws java.io.IOException {
            String error = StreamUtil.readString(stream);
            return new Reject(error);
        }

        @Override
        public void writeTo(java.io.OutputStream stream) throws java.io.IOException {
            StreamUtil.writeInt(stream, TAG);
            StreamUtil.writeString(stream, error);
        }

        public String getError() {
            return error;
        }
    }


}
