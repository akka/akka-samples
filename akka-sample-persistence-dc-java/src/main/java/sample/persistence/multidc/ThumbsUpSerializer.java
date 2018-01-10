package sample.persistence.multidc;

import akka.actor.ExtendedActorSystem;
import akka.serialization.SerializerWithStringManifest;
import com.google.protobuf.InvalidProtocolBufferException;
import sample.persistence.multidc.protobuf.ThumbsUpMessages;

import java.io.NotSerializableException;
import java.util.HashSet;

public class ThumbsUpSerializer extends SerializerWithStringManifest {

  private final ExtendedActorSystem system;

  private final String StateManifest = "a";
  private final String GiveThumbsUpManifest = "b";
  private final String GetCountManifest = "c";
  private final String GetUsersManifest = "d";
  private final String GaveThumbsUpManifest = "e";

  public ThumbsUpSerializer(ExtendedActorSystem system) {
    this.system = system;
  }

  @Override
  public int identifier() {
    return 100;
  }

  @Override
  public String manifest(Object o) {
    if (o instanceof ThumbsUpCounter.GiveThumbsUp) return GiveThumbsUpManifest;
    else if (o instanceof ThumbsUpCounter.GaveThumbsUp) return GaveThumbsUpManifest;
    else if (o instanceof ThumbsUpCounter.State) return StateManifest;
    else if (o instanceof ThumbsUpCounter.GetCount) return GetCountManifest;
    else if (o instanceof ThumbsUpCounter.GetUsers) return GetUsersManifest;
    else
      throw new IllegalArgumentException("Can't serialize object of type " + o.getClass() + " in " + getClass().getName());
  }

  @Override
  public byte[] toBinary(Object o) {
    if (o instanceof ThumbsUpCounter.GiveThumbsUp) return giveThumbsUpToBinary((ThumbsUpCounter.GiveThumbsUp) o);
    else if (o instanceof ThumbsUpCounter.GaveThumbsUp) return gaveThumbsUpToBinary((ThumbsUpCounter.GaveThumbsUp) o);
    else if (o instanceof ThumbsUpCounter.State) return stateToBinary((ThumbsUpCounter.State) o);
    else if (o instanceof ThumbsUpCounter.GetCount) return getCountToBinary((ThumbsUpCounter.GetCount) o);
    else if (o instanceof ThumbsUpCounter.GetUsers) return getUsersToBinary((ThumbsUpCounter.GetUsers) o);
    else
      throw new IllegalArgumentException("Cannot serialize object of type " + o.getClass().getName());
  }

  private byte[] stateToBinary(ThumbsUpCounter.State a) {
    return ThumbsUpMessages.State.newBuilder()
        .addAllUsers(a.users)
        .build().toByteArray();
  }

  private byte[] giveThumbsUpToBinary(ThumbsUpCounter.GiveThumbsUp a) {
    return ThumbsUpMessages.GiveThumbsUp.newBuilder()
        .setResourceId(a.resourceId)
        .setUserId(a.userId)
        .build().toByteArray();
  }

  private byte[] getCountToBinary(ThumbsUpCounter.GetCount a) {
    return ThumbsUpMessages.GetCount.newBuilder()
        .setResourceId(a.resourceId)
        .build().toByteArray();
  }

  private byte[] getUsersToBinary(ThumbsUpCounter.GetUsers a) {
    return ThumbsUpMessages.GetUsers.newBuilder()
        .setResourceId(a.resourceId)
        .build().toByteArray();
  }

  private byte[] gaveThumbsUpToBinary(ThumbsUpCounter.GaveThumbsUp a) {
    return ThumbsUpMessages.GaveThumbsUp.newBuilder()
        .setUserId(a.userId)
        .build().toByteArray();
  }

  @Override
  public Object fromBinary(byte[] bytes, String manifest) throws NotSerializableException {
    try {
      if (manifest.equals(GiveThumbsUpManifest)) return giveThumbsUpFromBinary(bytes);
      else if (manifest.equals(GaveThumbsUpManifest)) return gaveThumbsUpFromBinary(bytes);
      else if (manifest.equals(StateManifest)) return stateFromBinary(bytes);
      else if (manifest.equals(GetCountManifest)) return getCountFromBinary(bytes);
      else if (manifest.equals(GetUsersManifest)) return getUsersFromBinary(bytes);
      else
        throw new NotSerializableException(
            "Unimplemented deserialization of message with manifest [" + manifest + "] in " + getClass().getName());
    } catch (InvalidProtocolBufferException e) {
      throw new NotSerializableException(e.getMessage());
    }
  }

  private ThumbsUpCounter.State stateFromBinary(byte[] bytes) throws InvalidProtocolBufferException {
    ThumbsUpMessages.State a = ThumbsUpMessages.State.parseFrom(bytes);
    return new ThumbsUpCounter.State(new HashSet<>(a.getUsersList()));
  }

  private ThumbsUpCounter.GiveThumbsUp giveThumbsUpFromBinary(byte[] bytes) throws InvalidProtocolBufferException {
    ThumbsUpMessages.GiveThumbsUp a = ThumbsUpMessages.GiveThumbsUp.parseFrom(bytes);
    return new ThumbsUpCounter.GiveThumbsUp(a.getResourceId(), a.getUserId());
  }

  private ThumbsUpCounter.GetCount getCountFromBinary(byte[] bytes) throws InvalidProtocolBufferException {
    ThumbsUpMessages.GetCount a = ThumbsUpMessages.GetCount.parseFrom(bytes);
    return new ThumbsUpCounter.GetCount(a.getResourceId());
  }

  private ThumbsUpCounter.GetUsers getUsersFromBinary(byte[] bytes) throws InvalidProtocolBufferException {
    ThumbsUpMessages.GetUsers a = ThumbsUpMessages.GetUsers.parseFrom(bytes);
    return new ThumbsUpCounter.GetUsers(a.getResourceId());
  }

  private ThumbsUpCounter.GaveThumbsUp gaveThumbsUpFromBinary(byte[] bytes) throws InvalidProtocolBufferException {
    ThumbsUpMessages.GaveThumbsUp a = ThumbsUpMessages.GaveThumbsUp.parseFrom(bytes);
    return new ThumbsUpCounter.GaveThumbsUp(a.getUserId());
  }

}
