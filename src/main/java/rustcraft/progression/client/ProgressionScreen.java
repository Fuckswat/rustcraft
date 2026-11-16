package rustcraft.progression.client;

import java.util.*;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;
import net.minecraft.util.text.*;
import rustcraft.progression.*;

public final class ProgressionScreen extends ContainerScreen<ProgressionMenu> {
    private final List<Catalog.Recipe> recipes=new ArrayList<>();
    private int page,selected;
    private TextFieldWidget search;
    private int category=1;
    private static final String[] CATEGORIES={"Все","Старт","Стройка","Оружие","Патроны","Броня","Медицина","Станки","Транспорт","Материалы"};
    private String blocked="";
    private Button action;
    private final List<Button> rows=new ArrayList<>();
    public ProgressionScreen(ProgressionMenu menu,PlayerInventory inv,ITextComponent title) {super(menu,inv,title);if(menu.mode==4)category=0;xSize=320;ySize=240;for(Catalog.Recipe r:Catalog.RECIPES)if(menu.mode!=4||r.research>0)recipes.add(r);}
    private ITextComponent text(String key) {return new TranslationTextComponent("gui.rustcraft."+key);}
    @Override protected void init() {
        super.init();rows.clear();
        if(container.mode==5) {
            for(int i=0;i<Catalog.KIT_IDS.length;i++){final String id=Catalog.KIT_IDS[i];addButton(new Button(guiLeft+12,guiTop+38+i*40,132,24,new TranslationTextComponent("kit.rustcraft."+id),b->send(2,id)));}
        } else {
            addButton(new Button(guiLeft+8,guiTop+29,136,17,new StringTextComponent("Раздел: "+CATEGORIES[category]+" >"),b->{category=(category+1)%CATEGORIES.length;b.setMessage(new StringTextComponent("Раздел: "+CATEGORIES[category]+" >"));filter();}));
            search=new TextFieldWidget(font,guiLeft+10,guiTop+50,132,16,new StringTextComponent("Поиск предмета"));search.setMaxStringLength(48);search.setResponder(t->filter());children.add(search);
            for(int i=0;i<4;i++){final int row=i;rows.add(addButton(new Button(guiLeft+8,guiTop+69+i*17,136,16,new StringTextComponent(""),b->{selected=page*4+row;updateRows();})));}
            addButton(new Button(guiLeft+8,guiTop+140,45,18,new StringTextComponent("<"),b->{page=Math.max(0,page-1);selected=page*4;updateRows();}));
            addButton(new Button(guiLeft+99,guiTop+140,45,18,new StringTextComponent(">"),b->{page=Math.min(Math.max(0,(recipes.size()-1)/4),page+1);selected=page*4;updateRows();}));
            action=addButton(new Button(guiLeft+154,guiTop+139,158,16,text(container.mode==4?"research":"craft"),b->{if(selected<recipes.size())send(container.mode==4?1:0,recipes.get(selected).id);}));filter();
        }
    }
    private void send(int kind,String id) {ProgressionNetwork.CHANNEL.sendToServer(new ProgressionNetwork.Action(container.windowId,kind,id));}
    private void updateRows() {for(int i=0;i<rows.size();i++){int index=page*4+i;Button b=rows.get(i);b.visible=index<recipes.size();if(b.visible){Catalog.Recipe r=recipes.get(index);String name=r.output().getDisplayName().getString();b.setMessage(new StringTextComponent((index==selected?"> ":"")+font.func_238412_a_(name,120)));}}}
    private void filter() {
        recipes.clear();String q=search==null?"":search.getText().toLowerCase(java.util.Locale.ROOT).trim();
        for(Catalog.Recipe r:Catalog.RECIPES)if((container.mode!=4||r.research>0)&&matches(r)&&(q.isEmpty()||r.output().getDisplayName().getString().toLowerCase(java.util.Locale.ROOT).contains(q)||r.id.contains(q)))recipes.add(r);
        page=0;selected=0;updateRows();
    }
    private boolean matches(Catalog.Recipe r) {
        if(search!=null&&!search.getText().trim().isEmpty())return true;
        String id=r.id;Item item=r.output().getItem();
        switch(category) {
            case 1:return r.tier==0&&!id.contains("planks")&&!id.contains("stick")&&!id.contains("wool")&&!id.contains("leather");
            case 2:return item instanceof BlockItem&&!id.contains("workbench")&&!id.contains("research")&&!id.contains("furnace")&&!id.contains("refinery");
            case 3:return item instanceof rustcraft.guns.GunItem||item instanceof BowItem||item instanceof CrossbowItem;
            case 4:return id.contains("ammo_")||id.contains("arrow")||id.contains("gunpowder")||id.contains("explosive")||id.contains("grenade")||id.contains("satchel");
            case 5:return item instanceof ArmorItem;
            case 6:return id.contains("bandage")||id.contains("syringe")||id.contains("medkit");
            case 7:return id.contains("workbench")||id.contains("research_table")||id.contains("furnace")||id.contains("refinery");
            case 8:return id.contains("copter")||id.contains("heli")||id.contains("rhib");
            case 9:return !(item instanceof ArmorItem)&&!(item instanceof BlockItem)&&r.tier==0;
            default:return true;
        }
    }
    private int have(String id){int n=0;for(ItemStack s:minecraft.player.inventory.mainInventory)if(s.getItem()==Catalog.item(id))n+=s.getCount();return n;}
    private Map<String,Integer> needed(Catalog.Recipe r){Map<String,Integer> need=new LinkedHashMap<>();if(container.mode==4){need.put("rustcraft:scrap",ProgressionMenu.scaled(r.research,ProgressionClient.researchCost));need.put(r.id,1);}else r.costs.forEach((id,n)->need.put(id,ProgressionMenu.scaled(n,ProgressionClient.craftCost)));return need;}
    @Override public void tick() {
        super.tick();if(search!=null)search.tick();
        if(action==null)return;action.active=false;blocked="";
        if(recipes.isEmpty()){action.setMessage(new StringTextComponent("Ничего не найдено"));return;}
        Catalog.Recipe r=recipes.get(selected);boolean creative=minecraft.player.abilities.isCreativeMode;
        if(container.mode==4&&ProgressionClient.LEARNED.contains(r.id))blocked="Уже изучено";
        else if(!creative&&container.mode!=4&&r.research>0&&!ProgressionClient.LEARNED.contains(r.id))blocked="Сначала изучи на столе исследований";
        else if(!creative&&container.mode!=4&&ProgressionModule.nearbyTier(minecraft.player)<r.tier)blocked="Подойди к верстаку уровня "+r.tier;
        if(blocked.isEmpty()&&!creative)for(Map.Entry<String,Integer> c:needed(r).entrySet())if(have(c.getKey())<c.getValue()){blocked="Не хватает: "+new ItemStack(Catalog.item(c.getKey())).getDisplayName().getString()+" ×"+(c.getValue()-have(c.getKey()));break;}
        action.active=blocked.isEmpty();action.setMessage(new StringTextComponent(blocked.isEmpty()?(container.mode==4?"Изучить образец":"Создать ×"+r.count):font.func_238412_a_(blocked,148)));
    }
    @Override public boolean keyPressed(int key,int scan,int mods){if(search!=null&&search.isFocused()&&key!=256){search.keyPressed(key,scan,mods);return true;}return super.keyPressed(key,scan,mods);}
    @Override public boolean charTyped(char c,int mods){if(search!=null&&search.isFocused())return search.charTyped(c,mods);return super.charTyped(c,mods);}
    @Override protected void drawGuiContainerBackgroundLayer(MatrixStack m,float partial,int mx,int my) {
        fill(m,guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff202930);fill(m,guiLeft+4,guiTop+4,guiLeft+xSize-4,guiTop+28,0xff354650);fill(m,guiLeft+150,guiTop+29,guiLeft+314,guiTop+136,0xff172128);
        for(net.minecraft.inventory.container.Slot s:container.inventorySlots) {fill(m,guiLeft+s.xPos-1,guiTop+s.yPos-1,guiLeft+s.xPos+17,guiTop+s.yPos+17,0xff10191e);fill(m,guiLeft+s.xPos,guiTop+s.yPos,guiLeft+s.xPos+16,guiTop+s.yPos+16,0xff56636a);}
    }
    private void line(MatrixStack m,String s,int x,int y,int color) {font.drawString(m,s,x,y,color);}
    @Override protected void drawGuiContainerForegroundLayer(MatrixStack m,int mx,int my) {
        line(m,title.getString(),10,12,0xffe9edeb);
        if(container.mode==5) {
            line(m,I18n.format("gui.rustcraft.shared_cooldown"),151,40,0xffefcf8d);long seconds=Math.max(0,(ProgressionClient.kitReady-System.currentTimeMillis()+999)/1000);
            line(m,seconds>0?I18n.format("gui.rustcraft.wait",seconds/60,seconds%60):I18n.format("gui.rustcraft.kit_ready"),151,55,seconds>0?0xffedb070:0xff93db9a);
            String[] keys={"hunter_desc","builder_desc","medic_desc"};for(int i=0;i<keys.length;i++){line(m,I18n.format("gui.rustcraft."+keys[i]+"1"),151,78+i*20,0xffc0c9cd);line(m,I18n.format("gui.rustcraft."+keys[i]+"2"),151,88+i*20,0xffc0c9cd);}
        } else if(!recipes.isEmpty()) {
            Catalog.Recipe r=recipes.get(Math.min(selected,recipes.size()-1));ItemStack out=r.output();itemRenderer.renderItemAndEffectIntoGUI(out,156,32);itemRenderer.renderItemOverlayIntoGUI(font,out,156,32,null);
            line(m,font.func_238412_a_(out.getDisplayName().getString(),120),177,37,0xffe2ebed);
            boolean learned=ProgressionClient.LEARNED.contains(r.id)||r.research==0;
            line(m,r.tier==0?"Можно создать в руках":"Нужен верстак уровня "+r.tier,156,53,0xffc2cbd0);line(m,I18n.format(r.research==0?"gui.rustcraft.default_recipe":learned?"gui.rustcraft.learned":"gui.rustcraft.not_learned"),156,65,learned?0xff94d2a4:0xffe9b875);
            int y=79;
            if(container.mode==4) {cost(m,"rustcraft:scrap",ProgressionMenu.scaled(r.research,ProgressionClient.researchCost),y);cost(m,r.id,1,y+12);line(m,I18n.format("gui.rustcraft.sample_consumed"),156,y+38,0xffe9b875);}
            else {for(Map.Entry<String,Integer> c:r.costs.entrySet()){cost(m,c.getKey(),ProgressionMenu.scaled(c.getValue(),ProgressionClient.craftCost),y);y+=10;}line(m,"Слева: есть / справа: нужно",156,128,0xff99bdcd);}
            line(m,(page+1)+" / "+(Math.max(1,(recipes.size()+3)/4)),59,145,0xffdae1e2);
        }
        line(m,I18n.format("container.inventory"),8,160,0xffb5c1c7);
        if(!ProgressionClient.message.isEmpty()) {
            String remaining=I18n.format(ProgressionClient.message);
            for(int row=0;row<5&&!remaining.isEmpty();row++) {
                String part=font.func_238412_a_(remaining,66);
                if(part.length()<remaining.length()&&part.lastIndexOf(' ')>0)part=part.substring(0,part.lastIndexOf(' '));
                if(part.isEmpty())break;
                line(m,part,8,177+row*11,0xffe6c58b);remaining=remaining.substring(part.length()).trim();
            }
        }
    }
    private void cost(MatrixStack m,String id,int amount,int y) {Item item=Catalog.item(id);int have=0;for(ItemStack s:minecraft.player.inventory.mainInventory)if(s.getItem()==item)have+=s.getCount();String name=new ItemStack(item).getDisplayName().getString();line(m,font.func_238412_a_(name,94)+" "+have+" / "+amount,156,y,have>=amount?0xffa1d4a6:0xffe3a08d);}
    @Override public boolean mouseScrolled(double x,double y,double delta) {if(container.mode==5)return super.mouseScrolled(x,y,delta);page=Math.max(0,Math.min(Math.max(0,(recipes.size()-1)/4),page+(delta<0?1:-1)));selected=page*4;updateRows();return true;}
    @Override public void render(MatrixStack m,int x,int y,float p) {renderBackground(m);super.render(m,x,y,p);if(search!=null){search.render(m,x,y,p);if(search.getText().isEmpty()&&!search.isFocused())font.drawString(m,"Поиск: печь, лук, стена…",guiLeft+13,guiTop+54,0xff9aa8b0);}renderHoveredTooltip(m,x,y);if(action!=null&&!blocked.isEmpty()&&x>=guiLeft+154&&x<guiLeft+312&&y>=guiTop+139&&y<guiTop+155)renderTooltip(m,new StringTextComponent(blocked),x,y);}
}

