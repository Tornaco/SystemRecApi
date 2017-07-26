package dev.nick.library;

import android.os.RemoteException;

/**
 * Created by Tornaco on 2017/7/26.
 * Licensed with Apache.
 */

public class TokenAdapter extends IToken.Stub {
    @Override
    public String getDescription() throws RemoteException {
        return null;
    }

    @Override
    public void onDeny() throws RemoteException {

    }

    @Override
    public void onAllow() throws RemoteException {

    }

    @Override
    public void onAllowRemember() throws RemoteException {

    }
}
