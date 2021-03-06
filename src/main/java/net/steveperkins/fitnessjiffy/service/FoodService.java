package net.steveperkins.fitnessjiffy.service;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import net.steveperkins.fitnessjiffy.domain.Food;
import net.steveperkins.fitnessjiffy.domain.FoodEaten;
import net.steveperkins.fitnessjiffy.domain.User;
import net.steveperkins.fitnessjiffy.dto.FoodDTO;
import net.steveperkins.fitnessjiffy.dto.FoodEatenDTO;
import net.steveperkins.fitnessjiffy.dto.UserDTO;
import net.steveperkins.fitnessjiffy.repository.FoodEatenRepository;
import net.steveperkins.fitnessjiffy.repository.FoodRepository;
import net.steveperkins.fitnessjiffy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Date;
import java.util.*;

public final class FoodService {

    @Autowired
    UserRepository userRepository;

    @Autowired
    FoodRepository foodRepository;

    @Autowired
    FoodEatenRepository foodEatenRepository;

    @Autowired
    Converter<Food, FoodDTO> foodDTOConverter;

    @Autowired
    Converter<FoodEaten, FoodEatenDTO> foodEatenDTOConverter;

    @Nonnull
    public List<FoodEatenDTO> findEatenOnDate(
            @Nonnull final UUID userId,
            @Nonnull final Date date
    ) {
        final User user = userRepository.findOne(userId);
        final List<FoodEaten> foodEatens = foodEatenRepository.findByUserEqualsAndDateEquals(user, date);
        return foodsEatenToDTO(foodEatens);
    }

    @Nonnull
    public List<FoodDTO> findEatenRecently(
            @Nonnull final UUID userId,
            @Nonnull final Date currentDate
    ) {
        final User user = userRepository.findOne(userId);
        final Calendar calendar = new GregorianCalendar();
        calendar.setTime(currentDate);
        calendar.add(Calendar.DATE, -14);
        final Date twoWeeksAgo = new Date(calendar.getTime().getTime());
        final List<Food> recentFoods = foodEatenRepository.findByUserEatenWithinRange(
                user,
                new Date(twoWeeksAgo.getTime()),
                new Date(currentDate.getTime())
        );
        return foodsToDTO(recentFoods);
    }

    @Nullable
    public FoodEatenDTO findFoodEatenById(@Nonnull final UUID foodEatenId) {
        final FoodEaten foodEaten = foodEatenRepository.findOne(foodEatenId);
        return foodEatenToDTO(foodEaten);
    }

    public void addFoodEaten(
            @Nonnull final UUID userId,
            @Nonnull final UUID foodId,
            @Nonnull final Date date
    ) {
        final boolean duplicate = Iterables.any(findEatenOnDate(userId, date), new Predicate<FoodEatenDTO>() {
            @Override
            public boolean apply(@Nonnull final FoodEatenDTO foodAlreadyEaten) {
                return foodAlreadyEaten.getFood().getId().equals(foodId);
            }
        });
        if (!duplicate) {
            final User user = userRepository.findOne(userId);
            final Food food = foodRepository.findOne(foodId);
            final FoodEaten foodEaten = new FoodEaten(
                    UUID.randomUUID(),
                    user,
                    food,
                    date,
                    food.getDefaultServingType(),
                    food.getServingTypeQty()
            );
            foodEatenRepository.save(foodEaten);
        }
    }

    public void updateFoodEaten(
            @Nonnull final UUID foodEatenId,
            final double servingQty,
            @Nonnull final Food.ServingType servingType
    ) {
        final FoodEaten foodEaten = foodEatenRepository.findOne(foodEatenId);
        foodEaten.setServingQty(servingQty);
        foodEaten.setServingType(servingType);
        foodEatenRepository.save(foodEaten);
    }

    public void deleteFoodEaten(@Nonnull final UUID foodEatenId) {
        final FoodEaten foodEaten = foodEatenRepository.findOne(foodEatenId);
        foodEatenRepository.delete(foodEaten);
    }

    @Nonnull
    public List<FoodDTO> searchFoods(
            @Nonnull final UUID userId,
            @Nonnull final String searchString
    ) {
        final User user = userRepository.findOne(userId);
        final List<Food> foods = foodRepository.findByNameLike(user, searchString);
        return foodsToDTO(foods);
    }

    @Nullable
    public FoodDTO getFoodById(@Nonnull final UUID foodId) {
        final Food food = foodRepository.findOne(foodId);
        return foodToDTO(food);
    }

    @Nonnull
    public String updateFood(
            @Nonnull final FoodDTO foodDTO,
            @Nonnull final UserDTO userDTO
    ) {

        // TODO: Maybe this method should return some sort of ID, which maps to a message string elsewhere... rather than directly returning hardcoded strings meant for display.

        String resultMessage = "";
        // Halt if this operation is not allowed
        if (foodDTO.getOwnerId() == null || foodDTO.getOwnerId().equals(userDTO.getId())) {

            // Halt if this update would create two foods with duplicate names owned by the same user.
            final User user = userRepository.findOne(userDTO.getId());
            final List<Food> foodsWithSameNameOwnedByThisUser = foodRepository.findByOwnerEqualsAndNameEquals(user, foodDTO.getName());
            final boolean foundFoodWithSameNameOwnedByThisUser = Iterables.any(foodsWithSameNameOwnedByThisUser, new Predicate<Food>() {
                @Override
                public boolean apply(@Nonnull final Food food) {
                    return foodDTO.getId().equals(food.getId());
                }
            });
            if (foundFoodWithSameNameOwnedByThisUser) {
                resultMessage = "Error:  You already have another customized food with this name.";

            } else {

                // If this is already a user-owned food, then simply update it.  Otherwise, if it's a global food then create a
                // user-owned copy for this user.
                Food food = null;
                if (foodDTO.getOwnerId() == null) {
                    food = new Food();
                    food.setId(UUID.randomUUID());
                    food.setOwner(user);
                } else {
                    food = foodRepository.findOne(foodDTO.getId());
                }
                food.setName(foodDTO.getName());
                food.setDefaultServingType(foodDTO.getDefaultServingType());
                food.setServingTypeQty(foodDTO.getServingTypeQty());
                food.setCalories(foodDTO.getCalories());
                food.setFat(foodDTO.getFat());
                food.setSaturatedFat(foodDTO.getSaturatedFat());
                food.setCarbs(foodDTO.getCarbs());
                food.setFiber(foodDTO.getFiber());
                food.setSugar(foodDTO.getSugar());
                food.setProtein(foodDTO.getProtein());
                food.setSodium(foodDTO.getSodium());
                foodRepository.save(food);
                resultMessage = "Success!";
            }

        } else {
            resultMessage = "Error:  You are attempting to modify another user's customized food.";
        }
        return resultMessage;
    }

    @Nonnull
    public String createFood(
            @Nonnull final FoodDTO foodDTO,
            @Nonnull final UserDTO userDTO
    ) {

        // TODO: Maybe this method should return some sort of ID, which maps to a message string elsewhere... rather than directly returning hardcoded strings meant for display.

        String resultMessage = "";

        // Halt if this update would create two foods with duplicate names owned by the same user.
        final User user = userRepository.findOne(userDTO.getId());
        final List<Food> foodsWithSameNameOwnedByThisUser = foodRepository.findByOwnerEqualsAndNameEquals(user, foodDTO.getName());

        if (foodsWithSameNameOwnedByThisUser.isEmpty()) {
            final Food food = new Food();
            if (foodDTO.getId() == null) {
                food.setId(UUID.randomUUID());
            } else {
                food.setId(foodDTO.getId());
            }
            food.setOwner(user);
            food.setName(foodDTO.getName());
            food.setDefaultServingType(foodDTO.getDefaultServingType());
            food.setServingTypeQty(foodDTO.getServingTypeQty());
            food.setCalories(foodDTO.getCalories());
            food.setFat(foodDTO.getFat());
            food.setSaturatedFat(foodDTO.getSaturatedFat());
            food.setCarbs(foodDTO.getCarbs());
            food.setFiber(foodDTO.getFiber());
            food.setSugar(foodDTO.getSugar());
            food.setProtein(foodDTO.getProtein());
            food.setSodium(foodDTO.getSodium());
            foodRepository.save(food);
            resultMessage = "Success!";
        } else {
            resultMessage = "Error:  You already have another customized food with this name.";
        }
        return resultMessage;
    }

    @Nullable
    private FoodDTO foodToDTO(@Nullable final Food food) {
        return foodDTOConverter.convert(food);
    }

    @Nonnull
    private List<FoodDTO> foodsToDTO(@Nonnull final List<Food> foods) {
        return Lists.transform(foods, new Function<Food, FoodDTO>() {
            @Nullable
            @Override
            public FoodDTO apply(@Nullable final Food food) {
                return foodDTOConverter.convert(food);
            }
        });
    }

    @Nullable
    private FoodEatenDTO foodEatenToDTO(@Nullable final FoodEaten foodEaten) {
        return foodEatenDTOConverter.convert(foodEaten);
    }

    @Nonnull
    private List<FoodEatenDTO> foodsEatenToDTO(@Nonnull final List<FoodEaten> foodsEaten) {
        return Lists.transform(foodsEaten, new Function<FoodEaten, FoodEatenDTO>() {
            @Nullable
            @Override
            public FoodEatenDTO apply(@Nullable final FoodEaten foodEaten) {
                return foodEatenDTOConverter.convert(foodEaten);
            }
        });
    }

}
