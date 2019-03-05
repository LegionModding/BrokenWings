package quaternary.brokenwings;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentBase;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import quaternary.brokenwings.anticompat.*;
import quaternary.brokenwings.config.ListMode;
import quaternary.brokenwings.config.WingConfig;

import java.util.*;
import java.util.List;

@Mod(
	modid = BrokenWings.MODID,
	name = BrokenWings.NAME,
	version = BrokenWings.VERSION,
	guiFactory = "quaternary.brokenwings.config.asdf.GuiFactoryBlahblah"
)
@Mod.EventBusSubscriber(modid = BrokenWings.MODID)
public class BrokenWings {
	public static final String MODID = "brokenwings";
	public static final String NAME = "Broken Wings";
	public static final String VERSION = "2.0.0";
	
	public static final Logger LOGGER = LogManager.getLogger(NAME);
	
	public static final Map<String, Long> lastMessageTimes = new HashMap<>();
	public static final Random messageRandom = new Random();
	
	public static final int MESSAGE_COUNT = 9;
	
	@Mod.EventHandler
	public static void preinit(FMLPreInitializationEvent e) {
		WingConfig.preinit(e);
	}
	
	@Mod.EventHandler
	public static void init(FMLInitializationEvent e) {
		Countermeasures.createAll();
		
		WingConfig.init(e);
	}
	
	//shared to prevent reallocations, i guess (it's cleared every playertick anyways)
	private static final List<String> usedMethodNames = new ArrayList<>();
	
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void playerTick(TickEvent.PlayerTickEvent e) {
		if(WingConfig.MODE == ListMode.ALWAYS_ALLOW) return;
		EntityPlayer player = e.player;
		if(player.world.isRemote) return;
		if(player.isCreative() || player.isSpectator()) return;
		if(!WingConfig.MODE.isDimensionBanned(player.dimension)) return;
		
		EntityPlayerMP playerMP = (EntityPlayerMP) player;
		
		boolean isFlying = false;
		usedMethodNames.clear();
		for(ICountermeasure anti : Countermeasures.ENABLED) {
			if(anti.isFlying(playerMP)) {
				isFlying |= anti.tryStopFlying(playerMP);
				usedMethodNames.add(anti.getFriendlyName());
			}
		}
		
		if(isFlying) {
			playerMP.motionX = 0;
			playerMP.motionY -= 0.3;
			playerMP.motionZ = 0;
			playerMP./*isDirty*/isAirBorne = true;
			
			//have the player accept this new velocity
			playerMP.getServerWorld().getEntityTracker().sendToTrackingAndSelf(playerMP, new SPacketEntityVelocity(playerMP));
			
			if(WingConfig.SEND_STATUS_MESSAGE || WingConfig.SHOW_PARTICLES || WingConfig.PRINT_TO_LOG) {
				long now = playerMP.getServerWorld().getTotalWorldTime();
				
				if(now - lastMessageTimes.getOrDefault(playerMP.getName(), 0L) > 20 * WingConfig.EFFECT_INTERVAL) {
					lastMessageTimes.put(playerMP.getName(), now);
					
					if(WingConfig.SEND_STATUS_MESSAGE) {
						TextComponentBase msg;
						if(WingConfig.FIXED_MESSAGE.isEmpty()) {
							msg = new TextComponentTranslation("brokenwings.dropstatus." + messageRandom.nextInt(MESSAGE_COUNT));
						} else {
							msg = new TextComponentString(WingConfig.FIXED_MESSAGE);
						}
						
						playerMP.sendStatusMessage(msg, true);
					}
					
					if(WingConfig.SHOW_PARTICLES) {
						playerMP.getServerWorld().spawnParticle(EnumParticleTypes.TOTEM, playerMP.posX, playerMP.posY, playerMP.posZ, 45, 0, 0, 0, .2);
					}
					
					if(WingConfig.PRINT_TO_LOG) {
						LOGGER.info("Dropped " + playerMP.getName() + " out of the sky.");
						LOGGER.info("Dimension: " + playerMP.dimension);
						LOGGER.info("Position: " + niceBlockPosToString(playerMP.getPosition()));
						for(String method : usedMethodNames) {
							LOGGER.info("Method: " + method);
						}
					}
				}
			}
		}
	}
	
	private static String niceBlockPosToString(BlockPos pos) {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}
}
