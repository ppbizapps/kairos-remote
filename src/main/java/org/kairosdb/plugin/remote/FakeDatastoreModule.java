package org.kairosdb.plugin.remote;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import org.kairosdb.core.datastore.Datastore;
import org.kairosdb.core.datastore.ServiceKeyStore;

public class FakeDatastoreModule extends AbstractModule

{
	@Override
	protected void configure()
	{
		bind(Datastore.class).to(FakeDatastore.class).in(Scopes.SINGLETON);
		bind(ServiceKeyStore.class).to(FakeServiceKeyStore.class).in(Scopes.SINGLETON);
	}
}
