/*
 * This file ("BlockStair.java") is part of the Actually Additions Mod for Minecraft.
 * It is created and owned by Ellpeck and distributed
 * under the Actually Additions License to be found at
 * http://github.com/Ellpeck/ActuallyAdditions/blob/master/README.md
 * View the source code at https://github.com/Ellpeck/ActuallyAdditions
 *
 * © 2015 Ellpeck
 */

package ellpeck.actuallyadditions.blocks.base;


import cpw.mods.fml.common.registry.GameRegistry;
import ellpeck.actuallyadditions.creative.CreativeTab;
import ellpeck.actuallyadditions.util.ModUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;

public class BlockStair extends BlockStairs{

    private String name;

    public BlockStair(Block block, String name, int meta){
        super(block, meta);
        this.name = name;
        this.setLightOpacity(0);

        this.register();
    }

    public BlockStair(Block block, String name){
        this(block, name, 0);
    }

    private void register(){
        this.setBlockName(ModUtil.MOD_ID_LOWER+"."+this.getBaseName());
        GameRegistry.registerBlock(this, this.getItemBlock(), this.getBaseName());
        if(this.shouldAddCreative()){
            this.setCreativeTab(CreativeTab.instance);
        }
    }

    public boolean shouldAddCreative(){
        return true;
    }

    protected String getBaseName(){
        return this.name;
    }

    protected Class<? extends ItemBlockBase> getItemBlock(){
        return ItemBlockBase.class;
    }

    public EnumRarity getRarity(ItemStack stack){
        return EnumRarity.common;
    }
}