package movement;

import input.DangerMessageGenerator;
import movement.map.MapNode;
import core.Coord;
import core.Message;
import core.Settings;
import core.SimClock;

/**
 * 
 * This movement model makes use of several other movement models to simulate
 * movement in crisis situation.
 * 
 * @author Virginie Collombon, David San
 */
public class DangerMovement extends ExtendedMovementModel {

	public static final String PROBABILITY_TO_WALK = "walkProb";
	public static final String PROBABILITY_TO_BE_SELFWARNED = "selfwarnedProb";
	public static final String TIME_TO_WALK = "walkTime";

	private HomeMovement homeMM;
	private RandomPathMapBasedMovement walkMM;
	private ShortestPathMapBasedPoiMovement shortMM;
	private EvacuationCenterMovement2 evacMM;

	public static final int HOME_MODE = 0;
	public static final int WALK_MODE = 1;
	public static final int SHORT_MODE = 2;
	public static final int EVAC_MODE = 3;

	private int mode;

	private double selfwarnedProb;
	private double walkProb;
	private double walkTime;

	/**
	 * Creates a new instance of DangerMovement
	 * 
	 * @param settings
	 */
	public DangerMovement(Settings settings) {
		super(settings);
		homeMM = new HomeMovement(settings);
		shortMM = new ShortestPathMapBasedPoiMovement(settings);
		evacMM = new EvacuationCenterMovement2(settings);
		walkMM = new RandomPathMapBasedMovement(settings);

		walkProb = settings.getDouble(PROBABILITY_TO_WALK);
		selfwarnedProb = settings.getDouble(PROBABILITY_TO_BE_SELFWARNED);
		walkTime = settings.getDouble(TIME_TO_WALK);

		if (rng.nextDouble() > walkProb) {
			mode = HOME_MODE;
			setCurrentMovementModel(homeMM);
		} else {
			mode = WALK_MODE;
			setCurrentMovementModel(walkMM);
		}
		setHostMode();
	}

	/**
	 * Creates a new instance of DangerMovement from a prototype
	 * 
	 * @param proto
	 */
	public DangerMovement(DangerMovement proto) {
		super(proto);
		homeMM = new HomeMovement(proto.homeMM);
		shortMM = new ShortestPathMapBasedPoiMovement(proto.shortMM);
		evacMM = new EvacuationCenterMovement2(proto.evacMM);
		walkMM = new RandomPathMapBasedMovement(proto.walkMM);

		walkProb = proto.walkProb;
		selfwarnedProb = proto.selfwarnedProb;
		walkTime = proto.walkTime;

		if (rng.nextDouble() > walkProb) {
			mode = HOME_MODE;
			setCurrentMovementModel(homeMM);
		} else {
			mode = WALK_MODE;
			setCurrentMovementModel(walkMM);
		}
		setHostMode();
	}

	@Override
	public boolean newOrders() {
		switch (mode) {
		case HOME_MODE:
			// check for danger message
			for (Message m : this.host.getMessageCollection()) {
				if (m.getId()
						.toLowerCase()
						.contains(
								DangerMessageGenerator.MESSAGE_ID_PREFIX_S
										.toLowerCase())) {
					mode = SHORT_MODE;
					setHostMode();
					setCurrentMovementModel(shortMM);
					return true;
				}
			}
			// selfwarn
			if (rng.nextDouble() < selfwarnedProb) {
				this.host.getRouter().createNewMessage(
						new Message(host, host,
								DangerMessageGenerator.MESSAGE_ID_PREFIX_S
										+ host.getAddress(), 0));
				mode = SHORT_MODE;
				setHostMode();
				setCurrentMovementModel(shortMM);
			}
			break;
		case SHORT_MODE:
			if (shortMM.isReady()) {
				Coord coordLastMapNode = shortMM.lastMapNode.getLocation();
				// check if the node is at a evac center
				for (MapNode mn : shortMM.getPois().getPoiLists()) {
					Coord c = mn.getLocation();
					if (c.equals(coordLastMapNode)) {
						// the node is at the evacuation center
						mode = EVAC_MODE;
						setCurrentMovementModel(evacMM);
						break;
					}
				}
				setHostMode();
			}
			break;
		case EVAC_MODE:
			break;
		case WALK_MODE:
			walkMM.setLocation(getHost().getLocation()); // update his home
			mode = WALK_MODE;
			setCurrentMovementModel(walkMM);
			double walkTimeCurrent = SimClock.getTime();
			if (walkTimeCurrent > walkTime) { // check if time is up

				mode = HOME_MODE;
				setCurrentMovementModel(homeMM);
			} else {
				for (Message m : this.host.getMessageCollection()) {
					if (m.getId()
							.toLowerCase()
							.contains(
									DangerMessageGenerator.MESSAGE_ID_PREFIX_S
											.toLowerCase())) {
						shortMM.setLocation(host.getLocation());
						mode = SHORT_MODE;
						setCurrentMovementModel(shortMM);
						break;
					}
				}
			}
			setHostMode();
			break;
		default:
			break;
		}
		setHostMode();
		return true;
	}

	@Override
	public Coord getInitialLocation() {
		Coord homeLoc = shortMM.getInitialLocation().clone();
		walkMM.setLocation(homeLoc);
		shortMM.setLocation(homeLoc);
		return homeLoc;
	}

	@Override
	public MovementModel replicate() {
		return new DangerMovement(this);
	}

	private void setHostMode() {
		if (!(getHost() == null))
			getHost().setDangerMode(mode);
	}
}
