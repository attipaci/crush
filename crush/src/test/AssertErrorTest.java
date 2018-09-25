package test;

import java.util.ArrayList;



// 1. A simple object and a collection class for it...
class Item {
    int index;
}

class ItemGroup<ItemType extends Item> extends ArrayList<ItemType> {      
    
    // 2. The collection has a method that returns another collection
    //    with the same generic type, ItemType with an upper bound of Item.
    public ItemGroup<ItemType> getItems() {
        ItemGroup<ItemType> items = new ItemGroup<ItemType>();
        items.addAll(this);
        return items;
    }
    
    // 3. The collection has an abstract nested class, e.g. for processing elements...
    public abstract class Fork {
        public Fork() {}
        
        // 4. The nested class has an abstract method that takes
        //    the same generic type argument that the collection that instantiated
        //    this Fork consists of. The argument ItemType has an upper bound
        //    of Item by construct.
        public abstract void process(ItemType item); 
    }
}


// 5. A specific Item implementation with the corresponding specific collection implementation
//    They can be completely bare...
class MyItem extends Item {   
}


class MyGroup extends ItemGroup<MyItem> {
}


// 6. The test case that fails...
public class AssertErrorTest {
    public static void main(String[] args) {
        // We instantiate a new specific Item group, and get a second collection of
        // the same type of items from it...
        ItemGroup<?> items = new MyGroup().getItems();
        
        // We then try to create a type-restricted nested class to process that
        // group of channels. But, this trips the compiler...
        items.new Fork() {
            @Override
            public void process(Item item) { System.err.println(item.index); }
        }.process(items.get(0));
    }
}
