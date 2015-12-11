package info.code8.pbx;

/**
 * Created by code8 on 12/11/15.
 */

public class AuthKey {
    private final String keyId;
    private final String key;

    public AuthKey(String keyId, String key) {
        this.keyId = keyId;
        this.key = key;
    }

    public String getKeyId() {
        return keyId;
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AuthKey authKey = (AuthKey) o;

        return keyId.equals(authKey.keyId) && key.equals(authKey.key);

    }

    @Override
    public int hashCode() {
        int result = keyId.hashCode();
        result = 31 * result + key.hashCode();
        return result;
    }
}
