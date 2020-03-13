package dev.tycho.stonks.command.base;

import dev.tycho.stonks.command.base.autocompleters.ArgumentAutocompleter;
import dev.tycho.stonks.command.base.validators.ArgumentProvider;
import dev.tycho.stonks.command.base.validators.ArgumentStore;
import dev.tycho.stonks.command.base.validators.ArgumentValue;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public abstract class ModularCommandSub extends CommandSub {
  private final List<ArgumentProvider<?>> argumentValidators;
  private HashMap<String, ArgumentAutocompleter> autoCompleters = new HashMap<>();

  protected ModularCommandSub(ArgumentProvider<?>... arguments) {
    argumentValidators = new ArrayList<>(Arrays.asList(arguments));
  }

  public final void onCommand(Player player, String alias, String[] args) {
    //Create an argument store
    ArgumentStore store = new ArgumentStore();

    //Validate each argument
    int i = -1;
    for (ArgumentProvider<?> argument : argumentValidators) {
      i++;
      store.addArgument(argument.getName(), new ArgumentValue<>(null));
      if (i >= args.length - 1) {
        //We don't have enough args
        if (argument.isOptional()) {
          //If it is optional then stop looking for more
          break;
        } else {
          sendCorrectUsage(player, alias, args[0]);
          return;
        }
      }

      //If this is the last argument and it wants concatenated strings
      String argString;
      if (i == argumentValidators.size() - 1 && argument.isConcat()) {
        argString = concatArgs(i + 1, args);
      } else {
        argString = args[i + 1];
      }
      if (argument.parseArgument(argString) == null) {
        CommandBase.sendMessage(player, argument.getHelp());
        return;
      }
      store.addArgument(argument.getName(), new ArgumentValue<>(argument.parseArgument(argString)));
    }

    //At this point, all are validated and have a value
    execute(player, store);
  }

  public String getArgs() {
    StringBuilder usage = new StringBuilder();
    for (ArgumentProvider<?> argument : argumentValidators) {
      usage.append(" ").append(argument.getHelp());
    }
    return usage.toString();
  }

  private void sendCorrectUsage(Player player, String alias, String commandName) {
    String usage = "Correct usage: /" + alias + " " + commandName + getArgs();
    CommandBase.sendMessage(player, usage);
  }

  protected final <T> T getArgument(String name, ArgumentStore store) {
    @SuppressWarnings("unchecked") T value = (T) store.getArgument(name).getValue();
    return value;
  }

  protected void addAutocompleter(String argumentName, ArgumentAutocompleter autocompleter) {
    // Make sure an argument exists with the same argumentName
    for (ArgumentProvider<?> argument : argumentValidators) {
      if (argument.getName().equals(argumentName)) {
        autoCompleters.put(argumentName, autocompleter);
        return;
      }
    }
    throw new IllegalArgumentException("Argument name (" + argumentName + ") for autocompleter does not match an argument");
  }

  @Override
  public final List<String> getTabCompletions(Player player, String[] args) {
    List<String> completions = new ArrayList<>();
    //Attempt to get tab completions
    if (args.length - 2 >= argumentValidators.size()) {
      return null;
    }
    //Do we have an autocompleter for this argument?
    String argName = argumentValidators.get(args.length - 2).getName();
    //Add a prompt
    if (args[args.length - 1].isEmpty()) completions.add("<" + argName + ">");
    if (autoCompleters.containsKey(argName)) {
      //If we do, append the list of autocompletions for the argument
      completions.addAll(autoCompleters.get(argName).getCompletions(player, args[args.length - 1]));
    }
    return completions;
  }


  public abstract void execute(Player player, ArgumentStore store);


}
