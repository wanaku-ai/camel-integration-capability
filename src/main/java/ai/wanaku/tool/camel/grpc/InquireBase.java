package ai.wanaku.tool.camel.grpc;

import ai.wanaku.core.exchange.InquireReply;
import ai.wanaku.core.exchange.InquireRequest;
import ai.wanaku.core.exchange.InquirerGrpc;
import ai.wanaku.core.exchange.PropertySchema;
import io.grpc.stub.StreamObserver;
import java.util.Map;

public class InquireBase extends InquirerGrpc.InquirerImplBase {

    @Override
    public void inquire(InquireRequest request, StreamObserver<InquireReply> responseObserver) {
        responseObserver.onNext(InquireReply.newBuilder()
                .putAllProperties(properties())
                .build());
        responseObserver.onCompleted();
    }

    public Map<String, PropertySchema> properties() {
        return Map.of("yaml-file", toPropertySchema("The name of the yaml file", "string", true));
    }

    private static PropertySchema toPropertySchema(String description, String type, boolean required) {
        return PropertySchema.newBuilder()
                .setDescription(description)
                .setType(type)
                .setRequired(required)
                .build();
    }
}
