package org.apache.ignite.ci.user;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import org.apache.ignite.ci.analysis.IVersionedEntity;
import org.apache.ignite.ci.db.Persisted;
import org.apache.ignite.ci.util.CryptUtil;
import org.apache.ignite.ci.web.model.SimpleResult;
import org.jetbrains.annotations.Nullable;

import javax.ws.rs.FormParam;
import java.util.ArrayList;
import java.util.List;

import static javax.xml.bind.DatatypeConverter.printHexBinary;

@Persisted
public class TcHelperUser implements IVersionedEntity {
    public static final int LATEST_VERSION = 2;
    @SuppressWarnings("FieldCanBeLocal")
    public Integer _version = LATEST_VERSION;

    public String username;

    @Nullable
    public byte[] salt;

    @Nullable
    public byte[] userKeyKcv;

    @Nullable
    private List<Credentials> credentialsList = new ArrayList<>();

    public String fullName;

    public String email;

    @Override
    public int version() {
        return _version == null ? 0 : _version;
    }

    @Override
    public int latestVersion() {
        return LATEST_VERSION;
    }

    public Credentials getOrCreateCreds(String serverId) {
        Credentials next = getCredentials(serverId);

        if (next != null)
            return next;

        Credentials credentials = new Credentials();
        credentials.serverId = serverId;
        credentials.username = username;

        getCredentialsList().add(credentials);

        return credentials;
    }

    @Nullable
    public Credentials getCredentials(String serverId) {
        for (Credentials next : getCredentialsList()) {
            if (next.serverId.equals(serverId))
                return next;
        }

        return null;
    }

    public List<Credentials> getCredentialsList() {
        if (credentialsList == null)
            credentialsList = new ArrayList<>();

        return credentialsList;
    }

    public String getDisplayName() {
        if (!Strings.isNullOrEmpty(fullName))
            return fullName;

        return username;
    }

    public static class Credentials {
        String serverId;
        String username;

        byte[] passwordUnderUserKey;

        Credentials() {

        }

        public Credentials(String serviceId, String serviceLogin) {
            this.serverId = serviceId;
            this.username = serviceLogin;
        }


        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("serverId", serverId)
                    .add("username", username)
                    .add("passwordUnderUserKey", printHexBinary(passwordUnderUserKey))
                    .toString();
        }

        void setPasswordUnderUserKey(byte[] bytes) {
            passwordUnderUserKey = bytes;
        }


        public String getUsername() {
            return username;
        }

        public String getServerId() {
            return serverId;
        }

        public byte[] getPasswordUnderUserKey() {
            return passwordUnderUserKey;
        }

        public void setPassword(String password, byte[] userKey) {
            setPasswordUnderUserKey(
                    CryptUtil.aesEncryptP5Pad(
                            userKey,
                            password.getBytes(CryptUtil.CHARSET)));
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("username", username)
                .add("salt", salt == null ? "" : printHexBinary(salt))
                .add("userKeyKcv", userKeyKcv == null ? "" : printHexBinary(userKeyKcv))
                .add("credentialsList", credentialsList)
                .toString();
    }
}