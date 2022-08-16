/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.centralserver.restapi.dto.converter.db;

import ee.ria.xroad.common.identifier.XRoadObjectType;

import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.centralserver.openapi.model.ClientDto;
import org.niis.xroad.centralserver.openapi.model.ClientIdDto;
import org.niis.xroad.centralserver.openapi.model.XRoadIdDto;
import org.niis.xroad.centralserver.restapi.dto.converter.DtoConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.model.XRoadObjectTypeDtoConverter;
import org.niis.xroad.centralserver.restapi.entity.ClientId;
import org.niis.xroad.centralserver.restapi.entity.FlattenedSecurityServerClientView;
import org.niis.xroad.centralserver.restapi.entity.MemberClass;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient;
import org.niis.xroad.centralserver.restapi.entity.Subsystem;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.niis.xroad.centralserver.restapi.repository.MemberClassRepository;
import org.niis.xroad.centralserver.restapi.repository.SubsystemRepository;
import org.niis.xroad.centralserver.restapi.repository.XRoadMemberRepository;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static ee.ria.xroad.common.util.Fn.self;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_CLASS_NOT_FOUND;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientDtoConverter extends DtoConverter<SecurityServerClient, ClientDto> {

    private final ZoneOffset dtoZoneOffset;

    private final XRoadMemberRepository xRoadMemberRepository;
    private final MemberClassRepository memberClassRepository;
    private final SubsystemRepository subsystemRepository;

    private final ClientIdDtoConverter clientIdDtoConverter;

    @Override
    @SuppressWarnings("checkstyle:EmptyBlock")
    public ClientDto toDto(SecurityServerClient source) {
        ClientId clientId = source.getIdentifier();
        ClientIdDto clientIdDto = clientIdDtoConverter.toDto(clientId);

        ClientDto clientDto = new ClientDto();
        clientDto.setId(clientId.toShortString());
        clientDto.setXroadId(clientIdDto);
        clientDto.setCreatedAt(Option.of(source.getCreatedAt())
                .map(instant -> instant.atOffset(dtoZoneOffset))
                .getOrNull());
        clientDto.setUpdatedAt(Option.of(source.getUpdatedAt())
                .map(instant -> instant.atOffset(dtoZoneOffset))
                .getOrNull());

        if (source instanceof XRoadMember) {
            XRoadMember xRoadMember = (XRoadMember) source;
            clientDto.setMemberName(xRoadMember.getName());

        } else if (source instanceof Subsystem) {
            // do nothing

        } else {
            throw new IllegalStateException("Unknown client type: " + source.getClass().getName());
        }

        return clientDto;
    }

    @Override
    @SuppressWarnings("checkstyle:MissingSwitchDefault")
    public SecurityServerClient fromDto(ClientDto source) {
        ClientIdDto clientIdDto = source.getXroadId();
        ClientId clientId = clientIdDtoConverter.fromDto(clientIdDto);
        XRoadIdDto.TypeEnum clientType = clientIdDto.getType();

        if (clientType != null) {
            switch (clientType) {
                case MEMBER:
                    Supplier<XRoadMember> newXRoadMember = () -> {
                        String memberClassCode = clientIdDto.getMemberClass();
                        MemberClass memberClass = memberClassRepository
                                .findByCode(memberClassCode)
                                .getOrElseThrow(() -> new NotFoundException(
                                        MEMBER_CLASS_NOT_FOUND,
                                        "code",
                                        memberClassCode
                                ));
                        return new XRoadMember(
                                source.getMemberName(),
                                clientId,
                                memberClass
                        );
                    };

                    return xRoadMemberRepository
                            .findOneBy(clientId, XRoadObjectType.MEMBER)
                            .getOrElse(newXRoadMember);

                case SUBSYSTEM:
                    Supplier<Subsystem> newSubsystem = () -> {
                        XRoadMember xRoadMember = xRoadMemberRepository
                                .findOneBy(clientId.getMemberId(), XRoadObjectType.MEMBER)
                                .getOrElseThrow(() -> new NotFoundException(
                                        MEMBER_NOT_FOUND,
                                        "code",
                                        clientIdDto.getMemberCode()
                                ));
                        return new Subsystem(
                                xRoadMember,
                                clientId
                        );
                    };

                    return subsystemRepository
                            .findOneBy(clientId, XRoadObjectType.SUBSYSTEM)
                            .getOrElse(newSubsystem);
            }
        }

        throw new IllegalArgumentException("Invalid client type: " + clientType);
    }

    public <T extends SecurityServerClient> Function<SecurityServerClient, T> expectType(Class<T> clazz) {
        return securityServerClient -> {
            if (!clazz.isAssignableFrom(securityServerClient.getClass())) {
                throw new IllegalArgumentException(
                        "Invalid client type: " + securityServerClient.getIdentifier().getObjectType());
            }
            return (T) securityServerClient;
        };
    }

    @Service
    @RequiredArgsConstructor
    public class Flattened extends DtoConverter<FlattenedSecurityServerClientView, ClientDto> {

        private final XRoadObjectTypeDtoConverter.Service xRoadObjectTypeDtoMapper;

        @Override
        public ClientDto toDto(FlattenedSecurityServerClientView source) {
            return self(new ClientDto(), clientDto -> {
                clientDto.setId(String.valueOf(source.getId()));
                clientDto.setMemberName(source.getMemberName());
                clientDto.setXroadId(self(new ClientIdDto(), clientIdDto -> {
                    clientIdDto.setInstanceId(source.getXroadInstance());
                    MemberClass memberClass = source.getMemberClass();
                    Optional.ofNullable(memberClass)
                            .map(MemberClass::getCode)
                            .ifPresent(clientIdDto::setMemberClass);
                    clientIdDto.setMemberCode(source.getMemberCode());
                    clientIdDto.setSubsystemCode(source.getSubsystemCode());
                    clientIdDto.setType(xRoadObjectTypeDtoMapper.toDto(source.getType()));
                }));
                if (source.getCreatedAt() != null) {
                    clientDto.setCreatedAt(Option.of(source.getCreatedAt())
                            .map(instant -> instant.atOffset(dtoZoneOffset))
                            .getOrNull());
                }
                if (source.getUpdatedAt() != null) {
                    clientDto.setUpdatedAt(Option.of(source.getUpdatedAt())
                            .map(instant -> instant.atOffset(dtoZoneOffset))
                            .getOrNull());
                }
            });
        }

        @Override
        public FlattenedSecurityServerClientView fromDto(ClientDto source) {
            throw new UnsupportedOperationException();
        }
    }
}
