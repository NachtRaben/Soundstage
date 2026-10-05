package com.nachtraben.soundstage.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

public final class RecipeRig {
    private RecipeRig() {
    }

    public record Entry(String id, ItemStack produced, ItemStack expected) {
    }

    public static int countNamespaced(ServerLevel level, String namespace, String excludeContaining) {
        //? if >=1.21 {
        return (int) level.getServer().getRecipeManager().getRecipes().stream()
                .filter(holder -> idString(holder).contains(namespace + ":"))
                .filter(holder -> excludeContaining == null || !idString(holder).contains(excludeContaining))
                .count();
        //?} else {
        /*return (int) level.getServer().getRecipeManager().getRecipes().stream()
                .filter(recipe -> recipe.getId().toString().contains(namespace + ":"))
                .filter(recipe -> excludeContaining == null || !recipe.getId().toString().contains(excludeContaining))
                .count();
        *///?}
    }

    //? if >=1.21 {
    private static String idString(net.minecraft.world.item.crafting.RecipeHolder<?> holder) {
        //? if >=1.21.11 {
        /*return holder.id().identifier().toString();
        *///?} else {
        return holder.id().toString();
        //?}
    }
    //?}

    public static List<Entry> craftOwnIngredients(ServerLevel level, String namespace, boolean dropIngredient) {
        List<Entry> out = new ArrayList<>();
        //? if >=1.21.11 {
        /*for (var holder : level.getServer().getRecipeManager().getRecipes()) {
            net.minecraft.resources.ResourceLocation id = holder.id().identifier();
            if (!id.getNamespace().equals(namespace)) continue;
            if (!(holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe recipe)) continue;
            List<java.util.Optional<Ingredient>> ingredients;
            int width;
            int height;
            if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
                ingredients = shaped.getIngredients();
                width = shaped.getWidth();
                height = shaped.getHeight();
            } else if (recipe instanceof net.minecraft.world.item.crafting.ShapelessRecipe) {
                ingredients = recipe.placementInfo().ingredients().stream().map(java.util.Optional::of).toList();
                width = ingredients.size();
                height = 1;
            } else {
                continue;
            }
            List<ItemStack> stacks = new ArrayList<>();
            for (var ingredient : ingredients) {
                stacks.add(ingredient.flatMap(i -> i.items().findFirst())
                        .map(item -> new ItemStack(item.value())).orElse(ItemStack.EMPTY));
            }
            if (stacks.isEmpty() || stacks.size() != width * height || stacks.stream().allMatch(ItemStack::isEmpty)) continue;
            ItemStack expected = assemble(recipe, net.minecraft.world.item.crafting.CraftingInput.of(width, height, stacks), level);
            var input = net.minecraft.world.item.crafting.CraftingInput.of(width, height, dropOne(stacks, dropIngredient));
            ItemStack produced = level.getServer().getRecipeManager()
                    .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level)
                    .map(found -> assemble(found.value(), input, level))
                    .orElse(ItemStack.EMPTY);
            out.add(new Entry(id.toString(), produced, expected));
        }
        *///?} elif >=1.21 {
        for (var holder : level.getServer().getRecipeManager().getRecipes()) {
            net.minecraft.resources.ResourceLocation id = holder.id();
            if (!id.getNamespace().equals(namespace)) continue;
            if (!(holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe recipe)) continue;
            List<ItemStack> stacks = stacksFor(recipe.getIngredients(), dropIngredient);
            if (stacks.isEmpty()) continue;
            net.minecraft.world.item.crafting.CraftingInput input;
            if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
                if (stacks.size() != shaped.getWidth() * shaped.getHeight()) continue;
                input = net.minecraft.world.item.crafting.CraftingInput.of(shaped.getWidth(), shaped.getHeight(), stacks);
            } else {
                input = net.minecraft.world.item.crafting.CraftingInput.of(stacks.size(), 1, stacks);
            }
            ItemStack expected = recipe.getResultItem(level.registryAccess());
            ItemStack produced = level.getServer().getRecipeManager()
                    .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level)
                    .map(found -> found.value().assemble(input, level.registryAccess()))
                    .orElse(ItemStack.EMPTY);
            out.add(new Entry(id.toString(), produced, expected));
        }
        //?} else {
        /*for (var recipe0 : level.getServer().getRecipeManager().getRecipes()) {
            if (!recipe0.getId().getNamespace().equals(namespace)) continue;
            if (!(recipe0 instanceof net.minecraft.world.item.crafting.CraftingRecipe recipe)) continue;
            List<ItemStack> stacks = stacksFor(recipe.getIngredients(), dropIngredient);
            if (stacks.isEmpty()) continue;
            int width;
            int height;
            if (recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
                if (stacks.size() != shaped.getWidth() * shaped.getHeight()) continue;
                width = shaped.getWidth();
                height = shaped.getHeight();
            } else {
                width = stacks.size();
                height = 1;
            }
            SimpleCraftingContainer container = new SimpleCraftingContainer(width, height, stacks);
            ItemStack expected = recipe.getResultItem(level.registryAccess());
            ItemStack produced = level.getServer().getRecipeManager()
                    .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, container, level)
                    .map(found -> found.assemble(container, level.registryAccess()))
                    .orElse(ItemStack.EMPTY);
            out.add(new Entry(recipe0.getId().toString(), produced, expected));
        }
        *///?}
        return out;
    }

    public static ItemStack craftAlone(ServerLevel level, ItemStack stack) {
        //? if >=1.21.11 {
        /*var input = net.minecraft.world.item.crafting.CraftingInput.of(1, 1, List.of(stack));
        return level.getServer().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level)
                .map(held -> assemble(held.value(), input, level))
                .orElse(ItemStack.EMPTY);
        *///?} elif >=1.21 {
        var input = net.minecraft.world.item.crafting.CraftingInput.of(1, 1, List.of(stack));
        return level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level)
                .map(held -> held.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
        //?} else {
        /*SimpleCraftingContainer container = new SimpleCraftingContainer(1, 1, new ArrayList<>(List.of(stack)));
        return level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, container, level)
                .map(recipe -> recipe.assemble(container, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
        *///?}
    }

    //? if >=26.1 {
    /*private static ItemStack assemble(net.minecraft.world.item.crafting.CraftingRecipe recipe,
                                      net.minecraft.world.item.crafting.CraftingInput input, ServerLevel level) {
        return recipe.assemble(input);
    }
    *///?} elif >=1.21.11 {
    /*private static ItemStack assemble(net.minecraft.world.item.crafting.CraftingRecipe recipe,
                                      net.minecraft.world.item.crafting.CraftingInput input, ServerLevel level) {
        return recipe.assemble(input, level.registryAccess());
    }
    *///?}

    //? if <1.21.11 {
    private static List<ItemStack> stacksFor(List<Ingredient> ingredients, boolean dropOne) {
        if (ingredients.isEmpty()) return List.of();
        List<ItemStack> stacks = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            ItemStack[] options = ingredient.getItems();
            stacks.add(ingredient.isEmpty() || options.length == 0 ? ItemStack.EMPTY : options[0].copy());
        }
        return dropOne(stacks, dropOne);
    }
    //?}

    private static List<ItemStack> dropOne(List<ItemStack> stacks, boolean dropOne) {
        List<ItemStack> out = new ArrayList<>(stacks);
        if (dropOne) {
            for (int i = 0; i < out.size(); i++) {
                if (!out.get(i).isEmpty()) {
                    out.set(i, ItemStack.EMPTY);
                    break;
                }
            }
        }
        return out;
    }

    //? if <1.21 {
    /*private static final class SimpleCraftingContainer implements net.minecraft.world.inventory.CraftingContainer {
        private final int width;
        private final int height;
        private final List<ItemStack> items;

        SimpleCraftingContainer(int width, int height, List<ItemStack> items) {
            this.width = width;
            this.height = height;
            this.items = items;
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public List<ItemStack> getItems() {
            return items;
        }

        @Override
        public int getContainerSize() {
            return items.size();
        }

        @Override
        public boolean isEmpty() {
            return items.stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            return items.get(slot);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack removed = items.get(slot);
            items.set(slot, ItemStack.EMPTY);
            return removed;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return removeItemNoUpdate(slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            items.set(slot, stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            items.clear();
        }

        @Override
        public void fillStackedContents(net.minecraft.world.entity.player.StackedContents contents) {
            for (ItemStack stack : items) {
                contents.accountStack(stack);
            }
        }
    }
    *///?}
}
