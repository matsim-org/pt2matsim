package org.matsim.pt2matsim.config;

import java.util.Set;

import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.pt.transitSchedule.api.TransitRoute;

/**
 * Parameterset that define which network transport modes the router
 * can use for each schedule transport mode. If no networkModes are set, the
 * transit route is mapped artificially<p/>
 * <p>
 * Network transport modes are the ones in {@link Link#getAllowedModes()}, schedule
 * transport modes are from {@link TransitRoute#getTransportMode()}.
 **/
public class TransportModeParameterSet extends ReflectiveConfigGroup{
	
	static public final String GROUP_NAME = "transportModeAssignment";

	private static final String SCHEDULE_MODE = "scheduleMode";
	private static final String NETWORK_MODES = "networkModes";

	private String scheduleMode;
	private Set<String> networkModes;

	public TransportModeParameterSet() {
		super(GROUP_NAME);
	}
	
	public TransportModeParameterSet(String scheduleMode) {
		super(GROUP_NAME);
		this.scheduleMode = scheduleMode;
	}

	@StringGetter(SCHEDULE_MODE)
	public String getScheduleMode() {
		return scheduleMode;
	}

	@StringSetter(SCHEDULE_MODE)
	public void setScheduleMode(String scheduleMode) {
		this.scheduleMode = scheduleMode;
	}

	@StringGetter(NETWORK_MODES)
	public String getNetworkModesStr() {
		return CollectionUtils.setToString(networkModes);
	}

	@StringSetter(NETWORK_MODES)
	public void setNetworkModesStr(String networkModesStr) {
		this.networkModes = CollectionUtils.stringToSet(networkModesStr);
	}
	
	public Set<String> getNetworkModes() {
		return this.networkModes;
	}

}
