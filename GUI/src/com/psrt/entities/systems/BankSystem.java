package com.psrt.entities.systems;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.EntitySystem;
import com.artemis.utils.IntBag;
import com.psrt.containers.CanID;
import com.psrt.containers.CanValue;
import com.psrt.entities.components.DepositBox;
import com.psrt.entities.components.ProgressComponent;
import com.psrt.entities.components.TextComponent;

public class BankSystem extends EntitySystem {
	EntitySubscription sub;
	
	ComponentMapper<CanID> cm;
	ComponentMapper<TextComponent> tm;
	ComponentMapper<ProgressComponent> pm;
		
	long ticks = 0;
	private Bank bank;
	
	private boolean debug = false;;

	public BankSystem(Bank b) {
		super(Aspect.all(CanID.class));
		this.bank = b;
	}
	
	@Override
	protected void begin(){
		sub = super.getSubscription();
	}

	@Override
	protected void processSystem() {
		if(ticks == 0){
			cm = world.getMapper(CanID.class);
			tm = world.getMapper(TextComponent.class);
			pm = world.getMapper(ProgressComponent.class);
		}
		else{
			IntBag b = sub.getEntities();
			DepositBox box = bank.getTop();
			for(int i = 0; i < b.size(); i++){
				if(box != null) process(b.get(i), box);
				else {
					if(debug) System.out.println("Top box is null, ");
				}
			}
		}
		ticks++;
	}
	
	@SuppressWarnings("rawtypes")
	private void process(int entityId, DepositBox box){
		CanID id = cm.getSafe(entityId);
		CanValue value = null;
		if(id != null) {
			value = box.get(id);
		}
		TextComponent tc = tm.getSafe(entityId);
		ProgressComponent pc = (tc == null ? pm.getSafe(entityId) : null);
		if(value != null){
			if(tc != null){
				tc.setValue("" + value.getValue());
				if(debug) System.out.println("BankSystem.process(): Value - " + value.getValue() + ", Hash: " + id.hashCode());
			}else if(pc != null){
				pc.setValue(value.getValue());
			}
		}
	}
}