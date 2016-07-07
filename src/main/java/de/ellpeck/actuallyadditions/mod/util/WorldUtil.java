/*
 * This file ("WorldUtil.java") is part of the Actually Additions mod for Minecraft.
 * It is created and owned by Ellpeck and distributed
 * under the Actually Additions License to be found at
 * http://ellpeck.de/actaddlicense
 * View the source code at https://github.com/Ellpeck/ActuallyAdditions
 *
 * © 2015-2016 Ellpeck
 */

package de.ellpeck.actuallyadditions.mod.util;

import cofh.api.energy.IEnergyProvider;
import cofh.api.energy.IEnergyReceiver;
import de.ellpeck.actuallyadditions.mod.tile.TileEntityBase;
import de.ellpeck.actuallyadditions.mod.util.compat.TeslaUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;

public final class WorldUtil{

    public static void doEnergyInteraction(TileEntity tile){
        for(EnumFacing side : EnumFacing.values()){
            TileEntity otherTile = tile.getWorld().getTileEntity(tile.getPos().offset(side));
            if(otherTile != null){
                IEnergyReceiver handlerTo = null;
                IEnergyProvider handlerFrom = null;

                //Push RF
                if(tile instanceof IEnergyProvider && otherTile instanceof IEnergyReceiver){
                    handlerTo = (IEnergyReceiver)otherTile;
                    handlerFrom = (IEnergyProvider)tile;
                }
                //Pull RF
                else if(tile instanceof IEnergyReceiver && otherTile instanceof IEnergyProvider){
                    handlerTo = (IEnergyReceiver)tile;
                    handlerFrom = (IEnergyProvider)otherTile;
                }

                if(handlerFrom != null && handlerTo != null){
                    int drain = handlerFrom.extractEnergy(side, Integer.MAX_VALUE, true);
                    if(drain > 0){
                        if(handlerTo.canConnectEnergy(side.getOpposite())){
                            int filled = handlerTo.receiveEnergy(side.getOpposite(), drain, false);
                            handlerFrom.extractEnergy(side, filled, false);
                        }
                    }
                }
                else if(TileEntityBase.teslaLoaded){
                    TeslaUtil.doTeslaInteraction(tile, otherTile, side);
                }
            }
        }
    }

    public static void doFluidInteraction(TileEntity tile){
        for(EnumFacing side : EnumFacing.values()){
            TileEntity otherTile = tile.getWorld().getTileEntity(tile.getPos().offset(side));
            if(otherTile != null){
                for(int i = 0; i < 2; i++){
                    //Push and pull with old fluid system
                    if(tile instanceof net.minecraftforge.fluids.IFluidHandler && otherTile instanceof net.minecraftforge.fluids.IFluidHandler){
                        net.minecraftforge.fluids.IFluidHandler handlerTo = (net.minecraftforge.fluids.IFluidHandler)(i == 0 ? tile : otherTile);
                        net.minecraftforge.fluids.IFluidHandler handlerFrom = (net.minecraftforge.fluids.IFluidHandler)(i == 0 ? otherTile : tile);
                        FluidStack drain = handlerFrom.drain(side, Integer.MAX_VALUE, false);
                        if(drain != null){
                            if(handlerTo.canFill(side.getOpposite(), drain.getFluid())){
                                int filled = handlerTo.fill(side.getOpposite(), drain.copy(), true);
                                handlerFrom.drain(side, filled, true);
                                break;
                            }
                        }
                    }
                    //Push and pull with new fluid system
                    else{
                        IFluidHandler handlerFrom = (i == 0 ? tile : otherTile).getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, i == 0 ? side : side.getOpposite());
                        IFluidHandler handlerTo = (i == 0 ? otherTile : tile).getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, i == 0 ? side.getOpposite() : side);
                        if(handlerFrom != null && handlerTo != null){
                            FluidStack drain = handlerFrom.drain(Integer.MAX_VALUE, false);
                            if(drain != null){
                                int filled = handlerTo.fill(drain.copy(), true);
                                handlerFrom.drain(filled, true);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if a given Block with a given Meta is present in given Positions
     *
     * @param positions The Positions, an array of {xCoord, yCoord, zCoord} arrays containing Positions
     * @param block     The Block
     * @param meta      The Meta
     * @param world     The World
     * @return Is every block present?
     */
    public static boolean hasBlocksInPlacesGiven(BlockPos[] positions, Block block, int meta, World world){
        for(BlockPos pos : positions){
            IBlockState state = world.getBlockState(pos);
            if(!(state.getBlock() == block && block.getMetaFromState(state) == meta)){
                return false;
            }
        }
        return true;
    }

    public static ItemStack useItemAtSide(EnumFacing side, World world, BlockPos pos, ItemStack stack){
        if(world instanceof WorldServer && stack != null && stack.getItem() != null){
            BlockPos offsetPos = pos.offset(side);
            IBlockState state = world.getBlockState(offsetPos);
            Block block = state.getBlock();
            boolean replaceable = block.isReplaceable(world, offsetPos);

            //Fluids
            if(replaceable && !(block instanceof IFluidBlock) && !(block instanceof BlockLiquid)){
                FluidStack fluid = null;
                if(FluidContainerRegistry.isFilledContainer(stack)){
                    fluid = FluidContainerRegistry.getFluidForFilledItem(stack);
                }
                else if(stack.getItem() instanceof IFluidContainerItem){
                    fluid = ((IFluidContainerItem)stack.getItem()).getFluid(stack);
                }

                if(fluid != null && fluid.amount >= Util.BUCKET && fluid.getFluid().getBlock() != null && fluid.getFluid().getBlock().canPlaceBlockAt(world, offsetPos)){
                    if(world.setBlockState(offsetPos, fluid.getFluid().getBlock().getDefaultState(), 2)){
                        return stack.getItem().getContainerItem(stack);
                    }
                }
            }

            //Redstone
            if(replaceable && stack.getItem() == Items.REDSTONE){
                world.setBlockState(offsetPos, Blocks.REDSTONE_WIRE.getDefaultState(), 2);
                stack.stackSize--;
                return stack;
            }

            //Plants
            if(replaceable && stack.getItem() instanceof IPlantable){
                if(((IPlantable)stack.getItem()).getPlant(world, offsetPos).getBlock().canPlaceBlockAt(world, offsetPos)){
                    if(world.setBlockState(offsetPos, ((IPlantable)stack.getItem()).getPlant(world, offsetPos), 2)){
                        stack.stackSize--;
                        return stack;
                    }
                }
            }

            //Everything else
            try{
                if(world instanceof WorldServer){
                    FakePlayer fake = FakePlayerFactory.getMinecraft((WorldServer)world);
                    stack.onItemUse(fake, world, offsetPos, fake.getActiveHand(), side.getOpposite(), 0.5F, 0.5F, 0.5F);
                    return stack;
                }
            }
            catch(Exception e){
                ModUtil.LOGGER.error("Something that places Blocks at "+offsetPos.getX()+", "+offsetPos.getY()+", "+offsetPos.getZ()+" in World "+world.provider.getDimension()+" threw an Exception! Don't let that happen again!", e);
            }
        }
        return stack;
    }

    public static void dropItemAtSide(EnumFacing side, World world, BlockPos pos, ItemStack stack){
        BlockPos coords = pos.offset(side);
        EntityItem item = new EntityItem(world, coords.getX()+0.5, coords.getY()+0.5, coords.getZ()+0.5, stack);
        item.motionX = 0;
        item.motionY = 0;
        item.motionZ = 0;
        world.spawnEntityInWorld(item);
    }

    public static EnumFacing getDirectionBySidesInOrder(int side){
        switch(side){
            case 0:
                return EnumFacing.UP;
            case 1:
                return EnumFacing.DOWN;
            case 2:
                return EnumFacing.NORTH;
            case 3:
                return EnumFacing.EAST;
            case 4:
                return EnumFacing.SOUTH;
            default:
                return EnumFacing.WEST;
        }
    }

    public static EnumFacing getDirectionByPistonRotation(int meta){
        return EnumFacing.values()[meta];
    }

    public static ArrayList<Material> getMaterialsAround(World world, BlockPos pos){
        ArrayList<Material> blocks = new ArrayList<Material>();
        blocks.add(world.getBlockState(pos.offset(EnumFacing.NORTH)).getMaterial());
        blocks.add(world.getBlockState(pos.offset(EnumFacing.EAST)).getMaterial());
        blocks.add(world.getBlockState(pos.offset(EnumFacing.SOUTH)).getMaterial());
        blocks.add(world.getBlockState(pos.offset(EnumFacing.WEST)).getMaterial());
        return blocks;
    }

    public static boolean addToInventory(IInventory inventory, List<ItemStack> stacks, boolean actuallyDo, boolean shouldAlwaysWork){
        return addToInventory(inventory, stacks, EnumFacing.UP, actuallyDo, shouldAlwaysWork);
    }

    public static boolean addToInventory(IInventory inventory, List<ItemStack> stacks, EnumFacing side, boolean actuallyDo, boolean shouldAlwaysWork){
        return addToInventory(inventory, 0, inventory.getSizeInventory(), stacks, side, actuallyDo, shouldAlwaysWork);
    }

    //TODO This is disgusting and has to be updated to the capability system

    /**
     * Add an ArrayList of ItemStacks to an Array of slots
     *
     * @param inventory  The inventory to try to put the items into
     * @param stacks     The stacks to be put into the slots (Items don't actually get removed from there!)
     * @param side       The side to input from
     * @param actuallyDo Do it or just test if it works?
     * @return Does it work?
     */
    public static boolean addToInventory(IInventory inventory, int start, int end, List<ItemStack> stacks, EnumFacing side, boolean actuallyDo, boolean shouldAlwaysWork){
        //Copy the slots if just testing to later load them again
        ItemStack[] backupSlots = null;
        if(!actuallyDo){
            backupSlots = new ItemStack[inventory.getSizeInventory()];
            for(int i = 0; i < backupSlots.length; i++){
                ItemStack stack = inventory.getStackInSlot(i);
                if(stack != null){
                    backupSlots[i] = stack.copy();
                }
            }
        }

        int working = 0;
        for(ItemStack stackToPutIn : stacks){
            for(int i = start; i < end; i++){
                if(shouldAlwaysWork || ((!(inventory instanceof ISidedInventory) || ((ISidedInventory)inventory).canInsertItem(i, stackToPutIn, side)) && inventory.isItemValidForSlot(i, stackToPutIn))){
                    ItemStack stackInQuestion = inventory.getStackInSlot(i);
                    if(stackToPutIn != null && (stackInQuestion == null || (stackInQuestion.isItemEqual(stackToPutIn) && stackInQuestion.getMaxStackSize() >= stackInQuestion.stackSize+stackToPutIn.stackSize))){
                        if(stackInQuestion == null){
                            inventory.setInventorySlotContents(i, stackToPutIn.copy());
                        }
                        else{
                            stackInQuestion.stackSize += stackToPutIn.stackSize;
                        }
                        working++;

                        break;
                    }
                }
            }
        }

        //Load the slots again
        if(!actuallyDo){
            for(int i = 0; i < backupSlots.length; i++){
                inventory.setInventorySlotContents(i, backupSlots[i]);
            }
        }

        return working >= stacks.size();
    }

    public static int findFirstFilledSlot(ItemStack[] slots){
        for(int i = 0; i < slots.length; i++){
            if(slots[i] != null){
                return i;
            }
        }
        return 0;
    }

    public static RayTraceResult getNearestPositionWithAir(World world, EntityPlayer player, int reach){
        return getMovingObjectPosWithReachDistance(world, player, reach, false, false, true);
    }

    private static RayTraceResult getMovingObjectPosWithReachDistance(World world, EntityPlayer player, double distance, boolean p1, boolean p2, boolean p3){
        float f = player.rotationPitch;
        float f1 = player.rotationYaw;
        double d0 = player.posX;
        double d1 = player.posY+(double)player.getEyeHeight();
        double d2 = player.posZ;
        Vec3d vec3 = new Vec3d(d0, d1, d2);
        float f2 = MathHelper.cos(-f1*0.017453292F-(float)Math.PI);
        float f3 = MathHelper.sin(-f1*0.017453292F-(float)Math.PI);
        float f4 = -MathHelper.cos(-f*0.017453292F);
        float f5 = MathHelper.sin(-f*0.017453292F);
        float f6 = f3*f4;
        float f7 = f2*f4;
        Vec3d vec31 = vec3.addVector((double)f6*distance, (double)f5*distance, (double)f7*distance);
        return world.rayTraceBlocks(vec3, vec31, p1, p2, p3);
    }

    public static RayTraceResult getNearestBlockWithDefaultReachDistance(World world, EntityPlayer player){
        return getNearestBlockWithDefaultReachDistance(world, player, false, true, false);
    }

    public static RayTraceResult getNearestBlockWithDefaultReachDistance(World world, EntityPlayer player, boolean stopOnLiquids, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock){
        return getMovingObjectPosWithReachDistance(world, player, player instanceof EntityPlayerMP ? ((EntityPlayerMP)player).interactionManager.getBlockReachDistance() : 5.0D, stopOnLiquids, ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock);
    }

    //Cobbled together from Tinkers' Construct (with permission, thanks!) and PlayerInteractionManager code.
    //Breaking blocks is a hideous pain so yea.
    //This doesn't do any additional harvestability checks that the blocks itself don't do!
    public static boolean playerHarvestBlock(ItemStack stack, World world, EntityPlayer player, BlockPos pos){
        if(world.isAirBlock(pos)){
            return false;
        }

        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        if(player.capabilities.isCreativeMode){
            block.onBlockHarvested(world, pos, state, player);
            if(block.removedByPlayer(state, world, pos, player, false)){
                block.onBlockDestroyedByPlayer(world, pos, state);
            }

            if(!world.isRemote){
                world.playEvent(2001, pos, Block.getStateId(state));

                if(player instanceof EntityPlayerMP){
                    ((EntityPlayerMP)player).connection.sendPacket(new SPacketBlockChange(world, pos));
                }
            }

            return true;
        }

        stack.onBlockDestroyed(world, state, pos, player);

        if(!world.isRemote){
            if(player instanceof EntityPlayerMP){
                EntityPlayerMP playerMp = (EntityPlayerMP)player;

                int xp = ForgeHooks.onBlockBreakEvent(world, playerMp.interactionManager.getGameType(), playerMp, pos);
                if(xp == -1){
                    return false;
                }

                TileEntity tileEntity = world.getTileEntity(pos);
                if(block.removedByPlayer(state, world, pos, player, true)){
                    block.onBlockDestroyedByPlayer(world, pos, state);
                    block.harvestBlock(world, player, pos, state, tileEntity, stack);
                    block.dropXpOnBlockBreak(world, pos, xp);
                }

                world.playEvent(2001, pos, Block.getStateId(state));
                playerMp.connection.sendPacket(new SPacketBlockChange(world, pos));
                return true;
            }
        }
        else{
            if(block.removedByPlayer(state, world, pos, player, true)){
                block.onBlockDestroyedByPlayer(world, pos, state);
            }

            stack.onBlockDestroyed(world, state, pos, player);

            if(stack.stackSize <= 0 && stack == player.getHeldItemMainhand()){
                ForgeEventFactory.onPlayerDestroyItem(player, stack, EnumHand.MAIN_HAND);
                player.setHeldItem(EnumHand.MAIN_HAND, null);
            }

            Minecraft mc = Minecraft.getMinecraft();
            mc.getConnection().sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, mc.objectMouseOver.sideHit));

            return true;
        }
        return false;
    }
}
