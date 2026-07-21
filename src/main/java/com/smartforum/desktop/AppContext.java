package com.smartforum.desktop;

import com.smartforum.desktop.api.ApiClient;
import com.smartforum.desktop.auth.AuthService;
import com.smartforum.desktop.auth.Session;
import com.smartforum.desktop.db.LocalStore;
import com.smartforum.desktop.sync.SyncService;

/** Wires up and holds every shared, app-lifetime service. Created once in {@link Main}. */
public class AppContext {
    public final ApiClient api;
    public final LocalStore store;
    public final Session session;
    public final SyncService sync;
    public final AuthService auth;

    public AppContext() {
        this.api = new ApiClient();
        this.store = new LocalStore();
        this.session = new Session();
        this.sync = new SyncService(api, store);
        this.auth = new AuthService(api, store, session);
    }
}
