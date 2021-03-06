package sonar.flux.network;

import com.google.common.collect.Lists;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.DimensionManager;
import sonar.core.api.energy.EnergyType;
import sonar.core.helpers.NBTHelper;
import sonar.core.listener.ISonarListenable;
import sonar.core.listener.ListenableList;
import sonar.core.listener.PlayerListener;
import sonar.core.utils.CustomColour;
import sonar.core.utils.SimpleObservableList;
import sonar.flux.FluxConfig;
import sonar.flux.FluxEvents;
import sonar.flux.FluxNetworks;
import sonar.flux.api.AccessType;
import sonar.flux.api.network.FluxPlayer;
import sonar.flux.api.network.IFluxNetwork;
import sonar.flux.api.network.IFluxNetworkCache;
import sonar.flux.api.network.PlayerAccess;
import sonar.flux.connection.*;

import javax.annotation.Nullable;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** all the flux networks are created/stored/deleted here, an instance is found via the FluxAPI */
public class FluxNetworkCache implements IFluxNetworkCache, ISonarListenable<PlayerListener>, SimpleObservableList.IListWatcher<IFluxNetwork> {

	public int uniqueID = 1;
	private Map<UUID, List<IFluxNetwork>> networks = new HashMap<>();
	private static boolean INIT = false;

	public static FluxNetworkCache instance() {
		if(!INIT){
			///SERVER SIDE ONLY
			MapStorage storage = DimensionManager.getWorld(0).getMapStorage();
			if(storage.getOrLoadData(NetworkData.class, NetworkData.IDENTIFIER) == null) {
				storage.setData(NetworkData.IDENTIFIER, new NetworkData());
			}
			INIT = true;
		}
		return FluxNetworks.getServerCache();
	}

	public void clearNetworks() {
		networks.clear();
		uniqueID = 1;
		INIT = false;
	}

	private int createNewUniqueID() {
		return uniqueID++;
	}

	/** returns the first network for which the predicate is true */
	public IFluxNetwork getNetwork(Predicate<IFluxNetwork> found) {
		for (Entry<UUID, List<IFluxNetwork>> entry : networks.entrySet()) {
			Optional<IFluxNetwork> network = entry.getValue().stream().filter(found).findFirst();
			if(network.isPresent()){
				return network.get();
			}
		}
		return FluxNetworkInvalid.INVALID;
	}

	/** adds all networks for which the predicate is true to a new array list */
	public List<IFluxNetwork> getNetworks(Predicate<IFluxNetwork> found) {
		List<IFluxNetwork> list = new ArrayList<>();
		networks.values().forEach(NETWORKS -> NETWORKS.stream().filter(found).forEach(list::add));
		return list;
	}

	/** iterates every network connected */
	public void forEachNetwork(Consumer<IFluxNetwork> action){
		networks.values().forEach(l -> l.forEach(action));
	}

	/** returns a list of networks the player is allowed to connect to */
	public List<IFluxNetwork> getAllowedNetworks(EntityPlayer player, boolean admin) {
		return getNetworks(network -> admin || network.getPlayerAccess(player).canConnect());
	}

	/** gets a network with a specified unique id */
	public IFluxNetwork getNetwork(int iD) {
		return getNetwork(n -> !n.isFakeNetwork() && iD == n.getSetting(NetworkSettings.NETWORK_ID));
	}

	/** gets a list of all networks currently loaded */
	public List<IFluxNetwork> getAllNetworks() {
		List<IFluxNetwork> available = new ArrayList<>();
		networks.values().forEach(available::addAll);
		return available;
	}

	/** creates a new observable list, adding this Network Cache as a viewer allowing the monitoring of network changes */
	public List<IFluxNetwork> instanceNetworkList(){
		SimpleObservableList<IFluxNetwork> list = new SimpleObservableList<>();
		list.addWatcher(this);
		return list;
	}

	/** adds the given network to the cache */
	protected void addNetwork(IFluxNetwork network) {
		UUID owner = network.getSetting(NetworkSettings.NETWORK_OWNER);
		if (owner != null) {
			networks.computeIfAbsent(owner, (UUID) -> instanceNetworkList()).add(network);
		}
	}

	/** removes the given network from the cache */
	protected void removeNetwork(IFluxNetwork common) {
		UUID owner = common.getSetting(NetworkSettings.NETWORK_OWNER);
		common.onRemoved();
		if (owner != null && networks.get(owner) != null) {
			networks.get(owner).remove(common);
		}
	}

	/** checks the player hasn't reached their maximum network limit */
	public boolean hasSpaceForNetwork(EntityPlayer player) {
		if(FluxConfig.maximum_per_player == -1){
			return true;
		}
		UUID ownerUUID = FluxPlayer.getOnlineUUID(player);
		List<IFluxNetwork> created = networks.getOrDefault(ownerUUID, new ArrayList<>());
		return created.size() < FluxConfig.maximum_per_player;
	}

	public IFluxNetwork createNetwork(EntityPlayer player, String name, CustomColour colour, AccessType access, boolean disableConvert, EnergyType defaultEnergy) {
		UUID playerUUID = EntityPlayer.getUUID(player.getGameProfile());
		networks.computeIfAbsent(playerUUID, (UUID) -> instanceNetworkList());

		int iD = createNewUniqueID();

		FluxPlayer owner = FluxPlayer.createFluxPlayer(player, PlayerAccess.OWNER);
		FluxNetworkServer network = new FluxNetworkServer(iD, owner.getOnlineUUID(), owner.getCachedName(), name, colour, access, disableConvert, defaultEnergy);
		network.getSetting(NetworkSettings.NETWORK_PLAYERS).add(owner);

		addNetwork(network);
		FluxEvents.logNewNetwork(network);
		return network;
	}

	public void onPlayerRemoveNetwork(IFluxNetwork remove) {
		removeNetwork(remove);
		FluxEvents.logRemoveNetwork(remove);
	}

	public void onSettingsChanged(IFluxNetwork network) { //only called when saved settings are changed.
		List<PlayerListener> players = listeners.getListeners(FluxListener.SYNC_NETWORK_LIST);
		PacketFluxNetworkUpdate packet = new PacketFluxNetworkUpdate(Lists.newArrayList(network), NBTHelper.SyncType.SAVE, false);
		players.forEach(listener -> {if (network.getPlayerAccess(listener.player).canConnect())FluxNetworks.network.sendTo(packet, listener.player);});
	}


	@Override
	public void onElementAdded(@Nullable IFluxNetwork added) {
		List<PlayerListener> players = listeners.getListeners(FluxListener.SYNC_NETWORK_LIST);
		PacketFluxNetworkUpdate packet = new PacketFluxNetworkUpdate(Lists.newArrayList(added), NBTHelper.SyncType.SAVE, false);
		players.forEach(listener -> {if (added.getPlayerAccess(listener.player).canConnect())FluxNetworks.network.sendTo(packet, listener.player);});
	}

	@Override
	public void onElementRemoved(@Nullable IFluxNetwork remove) {
		List<PlayerListener> players = listeners.getListeners(FluxListener.SYNC_NETWORK_LIST);
		PacketNetworkDeleted packet = new PacketNetworkDeleted(remove);
		players.forEach(listener -> FluxNetworks.network.sendTo(packet, listener.player));
		updateNetworkListeners();
	}

	@Override
	public void onListChanged() {
		updateNetworkListeners();
	}

	//// LISTENERS \\\\

	private ListenableList<PlayerListener> listeners = new ListenableList<>(this, FluxListener.values().length);

	@Override
	public ListenableList<PlayerListener> getListenerList() {
		return listeners;
	}

	public void updateNetworkListeners() {
		List<PlayerListener> players = listeners.getListeners(FluxListener.SYNC_NETWORK_LIST);
		players.forEach(listener -> {
			List<IFluxNetwork> toSend = FluxNetworkCache.instance().getAllowedNetworks(listener.player, FluxHelper.isPlayerAdmin(listener.player));
			FluxNetworks.network.sendTo(new PacketFluxNetworkUpdate(toSend, NBTHelper.SyncType.SAVE, true), listener.player);
		});
	}

	public void updateAdminListeners() {
		List<PlayerListener> players = listeners.getListeners(FluxListener.ADMIN);
		players.forEach(listener -> {
			List<IFluxNetwork> toSend = FluxNetworkCache.instance().getAllowedNetworks(listener.player, true);
			FluxNetworks.network.sendTo(new PacketFluxNetworkUpdate(toSend, NBTHelper.SyncType.SAVE, true), listener.player);
		});
	}

	@Override
	public boolean isValid() {
		return true;
	}
}
